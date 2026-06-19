# 010-atc-radar-tracker

民航空中交通管制（ATC）分布式雷达航迹数据处理网关与监控端

## 项目架构

```
┌─────────────────┐     UDP (ASTERIX)     ┌──────────────────────┐
│  雷达数据源     │ ──────────────────────►│  ATC Radar Server    │
│  (CAT048/062)   │                        │  (Spring Boot)       │
└─────────────────┘                        └──────────┬───────────┘
                                                       │
                                              ┌───────▼───────┐
                                              │    Redis      │
                                              │  (航迹缓存)    │
                                              └───────┬───────┘
                                                       │ SSE
                                                       ▼
                                              ┌──────────────────┐
                                              │  ATC Frontend    │
                                              │  (Vue3 + OL)     │
                                              └──────────────────┘
```

## 技术栈

### 服务端 (atc-radar-server)
- **Java 17** + **Spring Boot 3.2**
- **UDP 监听**: 接收 Eurocontrol ASTERIX 协议二进制报文
- **ASTERIX 解析**: 通过位运算解析 Cat 048（单雷达航迹）和 Cat 062（融合航迹）
- **Redis**: 航迹状态缓存与分布式同步
- **SSE (Server-Sent Events)**: 实时推送航迹更新至前端

### 前端 (atc-radar-frontend)
- **Vue 3** + **Vite 5**
- **OpenLayers 8**: WebGIS 地图渲染
- **Element Plus**: UI 组件库
- **EventSource (SSE)**: 实时接收航迹数据
- **Canvas 动态图标**: 飞行器朝向指示与航向指引线

## 快速开始

### 方式一：本地开发

#### 前置条件
- JDK 17+
- Maven 3.9+
- Node.js 20+
- Redis 7+

#### 启动 Redis
```bash
docker-compose up -d
```

#### 启动服务端
```bash
cd atc-radar-server
mvn spring-boot:run
```
服务端默认端口：8080，UDP 监听端口：8600

#### 启动前端
```bash
cd atc-radar-frontend
npm install
npm run dev
```
前端默认地址：http://localhost:3000

### 方式二：Docker Compose 全栈部署
```bash
docker-compose -f docker-compose.full.yml up -d --build
```
- 前端：http://localhost
- 服务端 API：http://localhost:8080/api
- Redis：localhost:6379

## ASTERIX 协议支持

### CAT 048 - 单雷达航迹数据
- 数据项 I048/010 (SAC/SIC)
- 数据项 I048/020 (航迹编号)
- 数据项 I048/040 (时标)
- 数据项 I048/041 (坐标 WGS84)
- 数据项 I048/042 (飞行高度层)
- 数据项 I048/043 (航向)
- 数据项 I048/044 (地速)
- 数据项 I048/080 (目标地址 Mode S)
- 数据项 I048/090 (呼号)

### CAT 062 - 多传感器融合航迹
- 数据项 I062/015 (航迹编号)
- 数据项 I062/010 (时标)
- 数据项 I062/013 (坐标 WGS84)
- 数据项 I062/014 (飞行高度)
- 数据项 I062/017 (航向)
- 数据项 I062/018 (地速)
- 数据项 I062/019 (升降率)
- 数据项 I062/030 (目标地址)
- 数据项 I062/070 (呼号)

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tracks` | 获取全部活动航迹 |
| GET | `/api/tracks/{id}` | 获取指定航迹详情 |
| DELETE | `/api/tracks/{id}` | 删除指定航迹 |
| GET | `/api/tracks/stream` | SSE 实时航迹流 |
| GET | `/api/tracks/stats` | 获取统计信息 |
| POST | `/api/tracks/simulate` | 模拟航迹数据 |

## 配置项

### 服务端配置 (application.yml)
```yaml
atc:
  radar:
    udp:
      port: 8600              # UDP 监听端口
      buffer-size: 65535      # 接收缓冲区大小
    sse:
      heartbeat-interval: 15000  # SSE 心跳间隔(ms)
    track:
      ttl-seconds: 300        # 航迹过期时间(秒)
    simulator:
      enabled: true           # 是否启用模拟数据生成器
```

## 模拟数据

项目内置了航迹模拟器，默认启用，会生成 15 架虚拟飞机的实时航迹用于演示和测试。
