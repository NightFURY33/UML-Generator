# Java UML Text Tool

一个零依赖的 Java 命令行工具：把结构化文字转换成 PlantUML 文件。

## 运行

```bash
javac -d out src/main/java/com/example/umltool/UmlTool.java
java -cp out com.example.umltool.UmlTool examples/class.txt build/class.puml
```

也可以用 `Makefile`：

```bash
make examples
make jar
java -jar build/uml-tool.jar examples/class.txt build/class.puml
```

生成的 `build/class.puml` 可以用 PlantUML、IDE 插件或在线 PlantUML 服务渲染成 PNG/SVG。

如果你本机安装了 `plantuml` 命令，也可以直接渲染：

```bash
plantuml build/class.puml
```

## 输入格式

第一行用 `diagram:` 指定图类型：

```text
diagram: class
title "订单领域模型"
class User
field User - Long id
method User + login(email: String): boolean
class Order
relation User "1" -- "*" Order : places
```

支持的图类型：

- `class`: 类图
- `sequence`: 时序图
- `usecase`: 用例图

通用指令：

- `title "标题"` 设置标题
- `raw PlantUML语句` 直接输出 PlantUML 原始语句

## 类图指令

```text
class User
abstract BaseEntity
interface Repository
enum OrderStatus
field User - Long id
method User + login(email: String): boolean
relation User "1" -- "*" Order : places
```

## 时序图指令

```text
diagram: sequence
title "登录流程"
actor User
participant Web
participant AuthService
User -> Web: submit credentials
Web -> AuthService: authenticate()
AuthService --> Web: token
Web --> User: success
```

## 用例图指令

```text
diagram: usecase
title "商城用例"
actor Customer
actor Admin
usecase BrowseProducts
usecase ManageOrders
Customer -> BrowseProducts
Admin -> ManageOrders
```