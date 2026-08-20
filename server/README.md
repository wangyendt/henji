# 衡迹个人数据同步服务

服务端接收体重、身体成分、饮食结构化数据，以及从 vivo 健康分享图提取的单次跑步、步行和游泳记录；不上传饮食照片或健康截图。采用追加事件日志和当前对象墓碑，保证请求重试幂等，并阻止离线旧设备复活已经删除的记录。

## 接口

- `GET /healthz`
- `POST /v1/sync/push`
- `GET /v1/sync/pull?after=CURSOR&limit=200`

除健康检查外均要求 `Authorization: Bearer TOKEN`。

## 部署

1. 生成两个不同的随机密钥，分别写入 `secrets/db_password` 和 `secrets/api_token`，权限设为 `0600`。
2. 以 PostgreSQL 管理员执行：

   ```bash
   psql --set=health_sync_password="$(cat secrets/db_password)" \
     -f sql/000_health_sync_role.sql personal_knowledge
   psql -f sql/001_health_sync.sql personal_knowledge
   psql -f sql/002_weight_dedupe_key.sql personal_knowledge
   psql -f sql/003_normalize_weight_dedupe_key.sql personal_knowledge
   psql -f sql/004_daily_wellness_view.sql personal_knowledge
   psql -f sql/005_workout_records.sql personal_knowledge
   psql -f sql/006_analytics_views.sql personal_knowledge
   ```

3. `docker compose up -d --build`。

## 只读 Skill

`skills/henji-sync` 为 OpenClaw/Codex 提供健康数据的只读查询约定，覆盖体重、饮食、运动、墓碑和同步历史。Skill 不保存同步 Token 或数据库写入密码。
