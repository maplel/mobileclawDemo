---
name: expense-tracking
description: "记录消费、查看支出统计。当用户提到记账、消费、支出时使用。"
category: finance
version: "1"
allowed-tools:
  - write_sandbox_file
  - read_sandbox_file
context: inline
effort: low
risk: low
---

## 消费记录

### 记录消费
从用户输入提取：
- 金额
- 类别（餐饮、交通、购物等）
- 备注（可选）
- 日期（默认今天）

使用 `write_sandbox_file` 将记录追加到消费文件。

### 查看统计
使用 `read_sandbox_file` 读取消费记录，按类别和时间段汇总。
