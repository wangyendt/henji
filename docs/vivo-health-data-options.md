# vivo 健康睡眠、步数与运动数据接入调研

## 2026-08-20 真机分享验证结论

在 vivo V2458A、国内版 vivo 健康 6.5.5.20 上通过 ADB 实际进入页面验证：

- 睡眠详情页右上角“更多”只进入“睡眠设置”，没有分享入口；
- 单次运动详情页存在明确的“分享”按钮；
- 当前账号中的户外步行和泳池游泳都位于可分享的运动记录体系，跑步使用同一详情页结构；
- 因此衡迹的截图导入收缩为单次跑步、步行和游泳记录，不再解析或存储睡眠、每日活动、运动列表及周/月统计截图；
- 原 `daily_wellness_record` 已迁移为 `workout_record`，旧对象保留永久墓碑，事件历史仍在 `sync_events` 中。

更新时间：2026-08-20

## 结论

当前这台 vivo 手机上，**国内版 vivo 健康没有把睡眠、运动或手表步数写入 Android Health Connect**。因此，单纯给痕迹增加 `READ_SLEEP`、`READ_EXERCISE` 和 `READ_STEPS` 权限，仍然读不到 vivo 手表已经保存在 vivo 健康里的数据。

比较可行的路径按推荐顺序排列如下：

1. **申请 vivo Health Kit 云端接入**：这是取得 vivo 健康/手表数据的官方路径，适合作为长期方案；目前公开云接口明确包含步数和运动记录，睡眠接口需向 vivo 确认并申请相应权限。
2. **Health Connect + 手机自身计步**：可以较快补上“手机步数”，但不是 vivo 手表步数，也无法补上 vivo 手表睡眠。
3. **保留标准 Health Connect 读取能力**：读取其他支持 Health Connect 的数据源，并作为 vivo 未来开放同步后的自动兼容路径。

不建议把 Health Sync、Gadgetbridge、读取 vivo 健康私有数据库或无障碍抓取当成当前方案：前两者目前没有正式列出 vivo 健康/手表作为数据源，后两者脆弱且容易随系统或 App 更新失效。

## 本机核验结果

已通过 ADB 检查当前连接的真机：

| 项目 | 结果 |
| --- | --- |
| 设备 | vivo V2458A |
| Android | Android 16 / API 36 |
| Android 14 SDK Extension | 17（Health Connect 手机自身计步要求 20+） |
| 国内版 vivo 健康 | `com.vivo.health` 6.5.5.20 |
| Health Connect 控制器 | 已安装，`com.android.healthconnect.controller` |
| vivo 健康声明的标准 Health Connect 权限 | 没有发现任何 `android.permission.health.*` |
| 痕迹当前声明的 Health Connect 权限 | 体重、体脂、体水、骨量、去脂体重、基础代谢率和后台读取；没有步数、睡眠、运动权限 |

这说明此前读取失败有两个独立原因：

1. vivo 健康当前不是标准 Health Connect 数据写入方；
2. 痕迹当前也没有申请步数、睡眠和运动三类读取权限。

用户在“隐私管理 → vivo 健康服务平台”打开“健康服务”，开启的是 **vivo 自己的 Health Kit/云端健康数据服务**，不等于把数据写入 Google/Android Health Connect。

后续还可以在手机的 Health Connect 中人工复核：进入“数据和访问权限”，分别打开“活动 → 步数/锻炼”和“睡眠”，查看“数据来源”或“所有条目”。Google 的排查文档也建议通过条目来源确认究竟是哪个 App 写入了数据：[Health Connect 同步排查](https://support.google.com/android/answer/13770384)。

## 方案一：vivo Health Kit（推荐的完整官方路径）

### 能力与开放方式

vivo 官方把 Health Kit 定义为基于 vivo 账号和用户授权的运动健康数据开放平台，允许审核通过的应用读写运动健康数据，并明确举例第三方应用获取 vivo WATCH 的运动、心率，以及用睡眠和运动数据做分析：[vivo Health Kit 产品页](https://developers.vivo.com/product/d/healthKit)、[健康服务平台简介](https://developers.vivo.com/doc/d/bb71c60ceaf645b7af51a4365a5676bb)。

它面向开发者开放，但不是安装 SDK 后即可匿名读取：

1. 注册 vivo 商业账号并完成实名认证；
2. 在 vivo Health Kit 平台创建应用，取得 App ID 和 Secret；
3. 提交应用资质和所需数据权限；
4. 非敏感权限可自动审核，敏感权限进入人工审核，官方标注约 7 个工作日；
5. 测试联调完成后再次提交“发布服务”，发布审核也约 7 个工作日；
6. 服务受合同有效期约束。

官方流程见：[开发者注册介绍](https://developers.vivo.com/doc/d/fe1c4a7379334f508fcbc5345db88493)。Health Kit 的官方合作联系方式是 `iotpartners@vivo.com` / 微信 `vivopartners`：[vivo 开放平台联系方式](https://developers.vivo.com/doc/d/a74d0ce7896bd3a9a143691aecfe2fa9)。

### 当前公开接口实际覆盖情况

当前公开的云云接口文档明确给出了：

- H5 用户授权与一次性授权码；
- access token / refresh token；
- 用户授权状态和解绑；
- 运动记录列表、运动记录详情；
- vivo 健康向第三方服务器推送运动记录；
- 获取每日步数；
- HMAC-SHA256 请求签名。

接口文档：[vivo Health Kit 云云接入接口文档](https://developers.vivo.com/doc/d/4ea8ba1ec4cd44bd8bdaca9f3fecf795)。

需要特别注意：

- 当前公开云文档中**没有可直接实施的睡眠查询/推送接口**，尽管平台简介明确把睡眠列为应用场景。因此睡眠是否能通过审核后的私有权限或本地 SDK 获取，需要在申请前向 vivo 确认，不能仅凭简介承诺可用。
- 当前文档树中已不再公开原来的 “vivo Health Kit SDK 接口文档”，公开可见的主要是云云接入文档；本地端 SDK 是否继续向新合作方开放，也应一并询问 vivo。
- 云接口 FAQ 明确表示客户端不能直接接入云服务。因此 App 不应保存 vivo 的 App Secret，也不应直接调用 vivo 云接口。
- 服务器访问需要向 vivo 提供固定出口 IP 加白。
- 测试服务默认开放 50 天，完成后需要正式发布。
- vivo 推送到第三方的回调要求 HTTPS。
- 步数上云不是实时逐步推送；公开 FAQ 描述为距离上次上报超过半小时，或累计增加超过 1000 步时上报。

### 建议架构

```text
vivo 手表
   ↓
国内版 vivo 健康
   ↓ vivo Health Kit 云
hx470 / henji-sync-api
   ├─ OAuth 回调与 token 安全存储
   ├─ 接收运动推送
   ├─ 定时补拉步数/运动记录
   ├─ 幂等、墓碑、重试和数据归一化
   └─ 对痕迹 App 提供现有同步接口
              ↓
           痕迹 App
```

这条路径可以复用现有 hx470、PostgreSQL 和 `henji-sync-api`。vivo App Secret、access token 和 refresh token 只保存在服务器；手机只完成 H5 授权和读取自己服务器的归一化数据。

### 申请前应向 vivo 明确的五个问题

1. 国内版 vivo 健康 6.5.x 和当前手表型号是否可向第三方开放睡眠总时长及睡眠阶段；
2. 睡眠是云接口、推送接口还是仅限本地 SDK；
3. 日常步数、运动记录、心率和睡眠分别属于敏感还是非敏感权限；
4. 个人独立开发者/个人健康管理 App 是否接受申请，生产发布需要哪些企业资质和隐私材料；
5. 开发环境能否对单个 vivo 账号提供真实手表数据，以及固定出口 IP、回调域名和合同要求。

## 方案二：Health Connect 读取手机步数

Health Connect 本身支持 `StepsRecord`、`SleepSessionRecord` 和 `ExerciseSessionRecord`，对应权限分别是 `READ_STEPS`、`READ_SLEEP` 和 `READ_EXERCISE`：[Health Connect 数据类型和权限](https://developer.android.com/health-and-fitness/health-connect/data-types)。

Android 官方还提供“手机自身计步”：在 Android 14、SDK Extension 20 及以上设备上，只要有 App 获得 `READ_STEPS`，Health Connect 就可以使用手机的 `TYPE_STEP_COUNTER` 传感器记录手机步数：[读取手机步数](https://developer.android.com/health-and-fitness/health-connect/features/steps#read-mobile-steps)。当前真机的 Android 14 SDK Extension 是 **17**，尚未达到 20；需等待/安装后续 Google Play 系统更新才能使用这一条内置路径。

这条路径的边界是：

- 能得到随身携带手机时的步数；
- 当前真机系统扩展版本不足时，需改用痕迹自己的 `SensorManager.TYPE_STEP_COUNTER` 计步，或等待系统更新；
- 不能把 vivo 手表里已经记录的步数转移出来；
- 不能得到 vivo 手表睡眠；
- 手机未随身携带时会少计；
- 与其他来源合并时应使用 Health Connect 聚合 API，活动和睡眠是 Health Connect 会去重的类型：[聚合数据与读取限制](https://developer.android.com/health-and-fitness/health-connect/aggregate-data)。

如果实施，痕迹应增加三类权限，并在 UI 中明确展示数据来源，例如“手机计步”“某某 App 睡眠”，不要把手机计步误标成 vivo 手表数据。读取超过首次授权前 30 天的历史数据还需要 `READ_HEALTH_DATA_HISTORY`；后台读取需要 `READ_HEALTH_DATA_IN_BACKGROUND`。Google Play 发布时还要提交 Health Connect 数据用途声明。

## 方案三：保持标准 Health Connect 兼容层

即使 vivo 当前不写入 Health Connect，也值得在痕迹中加入以下标准读取能力：

- `StepsRecord`：每日步数；
- `ExerciseSessionRecord`：运动类型、开始/结束时间；
- `TotalCaloriesBurnedRecord` / `ActiveCaloriesBurnedRecord`：消耗；
- `SleepSessionRecord`：睡眠区间和阶段；
- 每条记录保留 `DataOrigin.packageName`，用于排错和防重复。

Health Connect 会在记录元数据中保存数据来源包名，适合做“数据来自哪里”的诊断页：[Health Connect 数据格式](https://developer.android.com/health-and-fitness/health-connect/data-format)。这既能接收其他健康 App/可穿戴设备的数据，也能在 vivo 将来支持标准同步时无需重构数据层。

## 当前不适合作为主方案的选项

### Health Sync

Health Sync 官方当前列出的 Android 数据源包括 Coros、Fitbit、Garmin、Google Fit、Health Connect、华为、Oura、Polar、Samsung Health、Strava、Suunto、Withings 等，**没有 vivo 健康**：[Health Sync 支持的数据源](https://healthsync.app/about/)。所以它不能作为 vivo 数据的现成桥接器。

### Gadgetbridge

Gadgetbridge 的官方设备列表当前没有 vivo/BlueOS 手表。对不支持的设备需要重新分析通信协议并实现设备适配，不是安装后即可导出数据：[Gadgetbridge 设备列表](https://gadgetbridge.org/gadgets/)、[新增设备说明](https://gadgetbridge.org/faq/)。它还可能要求解除 vivo 健康的配对并改用另一个伴侣 App，会影响现有手表功能。

### 国际版 Origin Health

Google Play 上另有国际版 `com.vivo.exhealth`，但它和国内版 `com.vivo.health` 的设备、账号和地区兼容性并不等价；公开页面也没有明确承诺将数据写入 Health Connect：[Origin Health](https://play.google.com/store/apps/details?id=com.vivo.exhealth)。不应为验证同步而直接替换当前国内版，避免破坏现有配对或造成数据割裂。

### 私有数据库、ContentProvider、无障碍或通知抓取

国内版 vivo 健康数据属于应用私有数据，正常第三方 App 不能直接读取。依靠 root、备份漏洞、私有 Provider、无障碍抓页面或反编译私有协议的实现，稳定性、系统升级兼容和隐私风险都明显高于 Health Kit；不适合成为日常自动同步方案。

## 推荐实施顺序

1. **先做只读诊断版本**：给痕迹增加步数、睡眠、运动 Health Connect 权限和数据来源诊断，不直接加入首页总结；真机验证 Health Connect 手机计步和当前各类型的实际来源。
2. **同时申请 vivo Health Kit**：先申请步数和运动，邮件明确询问睡眠权限/接口、本地 SDK 是否开放及个人开发者资质。
3. **申请通过后接 hx470**：由服务器处理 vivo OAuth、签名、固定出口 IP、运动推送和增量补拉，再经现有个人同步协议下发手机。
4. **数据模型分来源**：至少保留 `source`, `sourceRecordId`, `startTime`, `endTime`, `updatedAt`；睡眠、活动按来源和时间区间去重，避免 Health Connect 与 Health Kit 重复计入。
5. **申请结果出来前**：可先上线“手机步数”，睡眠模块在没有真实数据源时保持隐藏，不生成推测数据。
