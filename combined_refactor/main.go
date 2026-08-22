package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

var webUser, webPassword string
var webSessionMinutes int
var boolFlagNames = []string{"cli", "nsbtls", "progress", "nocolor", "compactipv4", "nsbcompact", "nsbqualified", "skipgeo"}



func rewriteBoolFlagArgs() {
	if len(os.Args) <= 2 {
		return
	}
	boolSet := map[string]struct{}{}
	for _, n := range boolFlagNames {
		boolSet[n] = struct{}{}
	}
	rewritten := append([]string(nil), os.Args[:1]...)
	for i := 1; i < len(os.Args); i++ {
		arg := os.Args[i]
		if name, ok := matchBoolFlag(arg, boolSet); ok && i+1 < len(os.Args) {
			next := strings.ToLower(os.Args[i+1])
			if next == "true" || next == "false" || next == "1" || next == "0" || next == "t" || next == "f" {
				rewritten = append(rewritten, "-"+name+"="+next)
				i++
				continue
			}
		}
		rewritten = append(rewritten, arg)
	}
	os.Args = rewritten
}

func matchBoolFlag(arg string, boolSet map[string]struct{}) (string, bool) {
	if !strings.HasPrefix(arg, "-") {
		return "", false
	}
	name := strings.TrimLeft(arg, "-")
	if strings.Contains(name, "=") {
		return "", false
	}
	if _, ok := boolSet[name]; ok {
		return name, true
	}
	return "", false
}

func hasNoColorArg() bool {
	for _, arg := range os.Args[1:] {
		name := strings.TrimLeft(arg, "-")
		if name == "nocolor" || strings.HasPrefix(name, "nocolor=") {
			if name == "nocolor" || strings.EqualFold(strings.TrimPrefix(name, "nocolor="), "true") {
				return true
			}
		}
	}
	return false
}

func main() {
	rewriteBoolFlagArgs()
	if !enableTerminalANSI() || os.Getenv("NO_COLOR") != "" || hasNoColorArg() {
		disableANSIColors()
	}
	cliCfg := registerCLIFlags()

	flag.IntVar(&listenPort, "port", 13335, "服务监听端口")
	flag.StringVar(&listenHost, "host", "", "服务监听地址；留空监听全部地址，Android APK 建议使用 127.0.0.1")
	flag.StringVar(&speedTestURL, "url", autoSpeedURLValue, "测速下载地址；auto 表示由后端自动选择内置测速源")
	flag.BoolVar(&skipGeoCheck, "skipgeo", false, "跳过地区/代理环境验证")
	flag.StringVar(&customDNSServer, "dns", defaultDNSServers, "自定义 DNS 服务器，例如 223.5.5.5、8.8.8.8:53 或逗号分隔多个；默认系统 DNS 优先、失败回退到该内置 DNS，显式提供时强制使用指定 DNS")
	flag.Var(debugFlagValue{}, "debug", "开启调试输出等级：error、all；也兼容 true/false，-debug 默认为 error")
	flag.StringVar(&webUser, "user", "", "Web 认证用户名（不设置则不启用认证）")
	flag.StringVar(&webPassword, "password", "", "Web 认证密码（需同时设置 -user）")
	flag.IntVar(&webSessionMinutes, "session", 720, "Web 登录会话有效期（分钟）")
	flag.Parse()
	if debugMode && flag.NArg() > 0 {
		for _, arg := range flag.Args() {
			if normalizeDebugLevel(arg) == "all" {
				debugLevel = "all"
			}
		}
	}
	flag.Visit(func(f *flag.Flag) {
		if f.Name == "dns" {
			customDNSForced = true
		}
	})
	if cliCfg.enabled {
		if err := prepareCLIConfig(cliCfg); err != nil {
			if errors.Is(err, errCLIConfigCreated) {
				return
			}
			recordProgramDebugError("cli_prepare", err.Error())
			fmt.Printf("CLI 执行失败: %v\n", err)
			return
		}
	}
	speedTestWorkers = cliCfg.speedTest
	configureHTTPClients()
	startupSpeedTestURL := speedTestURL
	if !cliCfg.enabled {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		resolvedSpeedURL, speedISP, err := resolveStartupSpeedTestURL(ctx, speedTestURL)
		cancel()
		if err != nil {
			recordDebugError("speed_isp_check", err.Error())
		} else {
			startupSpeedTestURL = resolvedSpeedURL
			recordDebugByLevel("all", "speed_isp_check", fmt.Sprintf("startup asn=%d org=%s mobile=%v selected=%s", speedISP.ASN, speedISP.ASOrganization, isChinaMobileISP(speedISP), currentAutoSpeedURLDefault()))
		}
	}
	if webSessionMinutes <= 0 {
		webSessionMinutes = 720
	}
	webSessionTTL = time.Duration(webSessionMinutes) * time.Minute

	if !cliCfg.enabled {
		cfgPath := filepath.Join(filepath.Dir(os.Args[0]), "大绿龙优选-config.json")
		if _, created, err := loadOrCreateCLIConfig(cfgPath); err != nil && !errors.Is(err, errCLIConfigCreated) {
			fmt.Printf("[config] 加载配置文件失败: %v\n", err)
		} else if created {
			fmt.Printf("[config] 已生成配置文件模板: %s\n", cfgPath)
		}
	}
	initLocations()
	if cliCfg.enabled {
		if err := runCLI(cliCfg); err != nil {
			recordProgramDebugError("cli_run", err.Error())
			fmt.Printf("CLI 执行失败: %v\n", err)
		}
		return
	}

	http.HandleFunc("/auth/login", func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			handleLoginPost(w, r)
			return
		}
		handleLoginPage(w, r)
	})
	http.HandleFunc("/auth/logout", handleLogout)
	http.HandleFunc("/favicon.png", func(w http.ResponseWriter, r *http.Request) {
		data, err := staticFiles.ReadFile("favicon.png")
		if err != nil {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "image/png")
		_, _ = w.Write(data)
	})

	http.HandleFunc("/", requireAuth(func(w http.ResponseWriter, r *http.Request) {
		data, err := staticFiles.ReadFile("index.html")
		if err != nil {
			http.Error(w, "无法加载页面", http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		_, _ = w.Write(data)
	}))
	http.HandleFunc("/ws", requireAuth(handleWebSocket))

	addr := fmt.Sprintf(":%d", listenPort)
	displayHost := "localhost"
	if strings.TrimSpace(listenHost) != "" {
		addr = fmt.Sprintf("%s:%d", strings.TrimSpace(listenHost), listenPort)
		displayHost = strings.TrimSpace(listenHost)
	}
	fmt.Printf("大绿龙优选 版本: %s\n", appVersion)
	displayURL := fmt.Sprintf("http://%s:%d", displayHost, listenPort)
	if webUser != "" && webPassword != "" {
		fmt.Printf("Web 认证已启用，用户名: %s\n", webUser)
		fmt.Printf("Web 会话有效期: %s 分钟\n", strconv.Itoa(webSessionMinutes))
	} else if webUser != "" || webPassword != "" {
		fmt.Println("警告： 需要同时设置 -user 和 -password 才会启用认证")
	}
	fmt.Printf("当前测速网址: %s\n", startupSpeedTestURL)
	if skipGeoCheck {
		fmt.Println("地区验证: 已跳过")
	} else {
		fmt.Println("地区验证: 启用")
	}
	if strings.TrimSpace(customDNSServer) == "" {
		fmt.Println("当前 DNS: 系统 DNS")
	} else {
		fmt.Printf("当前 DNS: %s\n", customDNSServer)
	}
	fmt.Printf("调试模式: %v\n", debugMode)
	if debugMode {
		fmt.Printf("调试等级: %s\n", normalizeDebugLevel(debugLevel))
		fmt.Printf("调试日志: %s\n", defaultDebugLogPath())
	}
	fmt.Printf("服务启动于 %s\n", displayURL)
	fmt.Printf("服务启动成功，复制 %s 到浏览器打开\n", displayURL)
	server := &http.Server{
		Addr:              addr,
		ReadHeaderTimeout: 10 * time.Second,
	}
	if err := server.ListenAndServe(); err != nil {
		fmt.Printf("启动失败: %v\n", err)
	}
}
