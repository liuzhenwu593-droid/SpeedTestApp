# 沙雕VPN (ShadiaoVPN)

基于 Android VPN Service + libv2ray 内核的代理客户端。

## 功能

- **多协议支持**: VLESS / VMess / Trojan / Shadowsocks (SS) / SSR
- **订阅管理**: 首次打开自动导入订阅，24小时自动更新
- **自动测速**: 打开 App / 打开节点列表时自动测速，支持全部测速
- **主题切换**: 白天 / 夜间 / 跟随系统（首次默认跟随系统）
- **代理模式**: 全局模式 / 分流模式（默认分流模式）
- **分应用代理**: 选择指定应用走代理（需 QUERY_ALL_PACKAGES 权限）
- **远程更新**: 通过 JSON 文件检查版本更新
- **TG 群入口**: 内置 Telegram 群链接
- **官网入口**: 内置官网链接

## 内置配置

| 配置项 | 值 |
|--------|-----|
| 包名 | `com.shadiao.nb` |
| 应用名 | 沙雕VPN |
| 订阅链接 | `https://meitu.ccwu.cc/sub?token=27882ec74d1d608cbc6d0f6756bc174f` |
| 更新 JSON | `https://yunpan.hynb.ccwu.cc/raw/update.json` |
| TG 群 | `https://t.me/shadiaovpn` |
| 官网 | `https://shadiao.hynb.ccwu.cc` |

## 构建

### 本地构建

1. 将 `libv2ray.aar` 放入 `app/libs/` 目录
2. 运行 `./gradlew assembleRelease`

### GitHub Actions 构建

项目已配置 `.github/workflows/build.yml`，推送到 GitHub 后会自动构建 APK。

如需签名，请在 GitHub 仓库 Settings → Secrets 中配置:
- `SIGNING_KEY`: keystore 文件的 Base64 编码
- `KEY_ALIAS`: 密钥别名
- `KEY_STORE_PASSWORD`: keystore 密码
- `KEY_PASSWORD`: 密钥密码

生成签名密钥:
```bash
keytool -genkey -v -keystore shadiao.jks -keyalg RSA -keysize 2048 -validity 10000 -alias shadiao
# 然后 Base64 编码
base64 shadiao.jks > keystore_base64.txt
```

## libv2ray.aar

项目依赖 `libv2ray.aar`，可从以下获取:
- [v2rayNG Releases](https://github.com/2dust/v2rayNG/releases)
- 自行编译 [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite)

将 AAR 文件放入 `app/libs/` 目录即可。

## 技术栈

- Kotlin + Coroutines
- AndroidX / Material Components
- OkHttp (网络请求)
- WorkManager (定时任务)
- kotlinx.serialization (JSON 解析)
- libv2ray (V2Ray 核心)

## License

MIT
