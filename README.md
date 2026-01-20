Android Calendar App 

一款基于 Android (Kotlin) 的现代化个人日程管理应用，支持农历、多视图切换及标准 ICS 格式导入导出。

项目简介

本项目旨在解决传统日历应用“数据孤岛”的问题。除了基础的增删改查和闹钟提醒功能外，重点实现了符合 RFC 5545 标准的数据导入导出和网络订阅功能，并内置了高精度的农历与节假日显示算法。

 核心功能

  多维视图：支持 年 / 月 / 周 三种视图无缝切换。

  农历支持：日期下方显示农历，法定节假日（春节、国庆等）红色高亮。

数据互通： 

  导出日程为 .ics 文件。

  从 .ics 文件导入日程。

订阅网络日历（如节假日安排）。

智能提醒：集成 AlarmManager，支持准时或提前提醒。

精美 UI：Material Design 风格，圆角卡片设计，实心/空心圆选中效果。

技术栈

开发语言：Kotlin

UI 组件：ConstraintLayout, RecyclerView, Material Components

日历控件：Kizitonwose CalendarView

数据库：Google Jetpack Room (SQLite)

异步处理：Kotlin Coroutines (协程) + Flow

网络请求：OkHttp

工具算法：ChineseCalendar (ICU4J), 正则表达式状态机解析
