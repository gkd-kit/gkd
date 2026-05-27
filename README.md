# GKD-Plus

基于 [GKD](https://github.com/gkd-kit/gkd) 的增强版本，在原版基础上新增 AI 智能规则生成等功能。

原作者不希望维护过多功能，因此本项目作为独立分支继续开发更多实用特性。

## 新增功能

### AI 智能规则生成

- 支持接入大模型（OpenAI / Anthropic 协议）自动生成点击规则
- 配置灵活：可自定义 API 地址、模型、温度等参数
- 请求队列机制：多次快照不会丢失，按顺序依次处理

### 加强模式（双击快照按钮触发）

- 双击悬浮球触发加强验证流程
- 自动生成规则 → 执行动作 → 验证效果 → 失败自动重试
- 对比执行前后节点树变化，判断规则是否生效
- 最多重试 2 次，重试时携带失败上下文供 AI 参考

### AI 配置备份/恢复

- 大模型配置（API URL、Key、模型等）纳入备份/导入体系

## 原版功能

基于 [高级选择器](https://gkd.li/guide/selector) + [订阅规则](https://gkd.li/guide/subscription) + [快照审查](https://github.com/gkd-kit/inspect) 的自定义屏幕点击 Android 应用

- **快捷操作** - 简化重复流程，如自动确认电脑登录
- **跳过流程** - 自动跳过启动时的烦人流程

## 免责声明

**本项目遵循 [GPL-3.0](/LICENSE) 开源，项目仅供学习交流，禁止用于商业或非法用途**

## 安装

前往 [Releases](https://github.com/fjjzy/gkd/releases) 下载最新版本

## 订阅

GKD **默认不提供规则**，需自行添加本地规则，或者通过订阅链接的方式获取远程规则

也可通过 [subscription-template](https://github.com/gkd-kit/subscription-template) 快速构建自己的远程订阅

第三方订阅列表可在 <https://github.com/topics/gkd-subscription> 查看

## 选择器

一个类似 CSS 选择器的选择器, 能联系节点上下文信息, 更容易也更精确找到目标节点

<https://gkd.li/guide/selector>

## 致谢

- [GKD](https://github.com/gkd-kit/gkd) - 原项目
- [lisonge](https://github.com/lisonge) - 原作者
