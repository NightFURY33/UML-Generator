package main.java.com.example.umltool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


 //UML转换工具核心主类

public final class UmlTool {
    private UmlTool() {
    }


     //主入口：参数校验、文件读写流控制

    public static void main(String[] args) throws IOException {
        // 1. 命令行参数校验，若不符合要求则输出使用说明
        if (args.length < 1 || args.length > 2 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            return;
        }

        Path input = Path.of(args[0]);
        // 2. 确定输出路径：若未指定第二个参数，则默认生成同名 .puml 文件
        Path output = args.length == 2 ? Path.of(args[1]) : defaultOutputPath(input);

        // 3. 读取自定义DSL文本内容
        String source = Files.readString(input, StandardCharsets.UTF_8);

        // 4. 实例化解析器
        String plantUml = new Parser(source).parse().render();

        // 5. 自动创建输出目录并写入生成的源码文件
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, plantUml, StandardCharsets.UTF_8);
        System.out.println("Generated " + output.toAbsolutePath());
    }


     //计算默认输出路径

    private static Path defaultOutputPath(Path input) {
        String fileName = input.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        return input.resolveSibling(baseName + ".puml");
    }


     //打印使用说明
    private static void printUsage() {
        System.out.println("Usage: java -cp out com.example.umltool.UmlTool <input.txt> [output.puml]");
        System.out.println("Example: java -cp out com.example.umltool.UmlTool examples/class.txt build/class.puml");
    }


     //支持的图表类型枚举

    private enum DiagramType {
        CLASS,     // 类图
        SEQUENCE,  // 时序图
        USECASE    // 用例图
    }


     //统一的多态渲染接口

    private interface Diagram {
        String render(); // 负责吐出标准的 PlantUML 字符串
    }


     //核心语法解析器（内部静态类）

    private static final class Parser {
        private final List<Line> lines; // 存放经过清洗后的有效行

        private Parser(String source) {
            this.lines = normalize(source); // 初始化时直接进行文本流“脱水清洗”
        }


         //语法分析：根据图表类型分发给具体解析器

        private Diagram parse() {
            DiagramType type = null;
            // 1. 扫描寻找指令行（如：diagram: class）确定图表类型
            for (Line line : lines) {
                List<String> tokens = tokenize(line.text());
                if (tokens.size() >= 2 && "diagram:".equalsIgnoreCase(tokens.get(0))) {
                    type = parseDiagramType(tokens.get(1), line.number());
                    break;
                }
            }

            if (type == null) {
                throw new IllegalArgumentException("Missing required directive: diagram: class|sequence|usecase");
            }

            // 2. 利用 JDK 新特性 switch 表达式进行多态分发
            return switch (type) {
                case CLASS -> parseClassDiagram();
                case SEQUENCE -> parseSequenceDiagram();
                case USECASE -> parseUseCaseDiagram();
            };
        }


         //解析类图（Class Diagram）

        private ClassDiagram parseClassDiagram() {
            ClassDiagram diagram = new ClassDiagram();
            for (Line line : lines) {
                List<String> tokens = tokenize(line.text());
                if (tokens.isEmpty() || isDiagramDirective(tokens)) {
                    continue;
                }

                String command = tokens.get(0).toLowerCase(Locale.ROOT);
                switch (command) {
                    case "title" -> diagram.title = restAfterCommand(line.text());
                    case "class", "abstract", "interface", "enum" -> diagram.addClassifier(command, requireToken(tokens, 1, line));
                    case "field" -> diagram.addMember(requireToken(tokens, 1, line), restAfterTokens(tokens, 2));
                    case "method" -> diagram.addMember(requireToken(tokens, 1, line), restAfterTokens(tokens, 2));
                    case "relation" -> diagram.relations.add(restAfterCommand(line.text()));
                    case "raw" -> diagram.rawLines.add(restAfterCommand(line.text()));
                    default -> throw unknownCommand(line, command);
                }
            }
            return diagram;
        }


         //解析时序图（Sequence Diagram）

        private SimpleDiagram parseSequenceDiagram() {
            SimpleDiagram diagram = new SimpleDiagram("sequence");
            for (Line line : lines) {
                List<String> tokens = tokenize(line.text());
                if (tokens.isEmpty() || isDiagramDirective(tokens)) {
                    continue;
                }

                String command = tokens.get(0).toLowerCase(Locale.ROOT);
                switch (command) {
                    case "title" -> diagram.title = restAfterCommand(line.text());
                    case "actor", "participant", "boundary", "control", "entity", "database", "collections", "queue" ->
                            diagram.lines.add(command + " " + quoteIfNeeded(requireToken(tokens, 1, line)));
                    case "raw" -> diagram.lines.add(restAfterCommand(line.text()));
                    default -> {
                        // 如果包含箭头符号，直接视作标准时序连线加入
                        if (line.text().contains("->") || line.text().contains("-->")) {
                            diagram.lines.add(line.text());
                        } else {
                            throw unknownCommand(line, command);
                        }
                    }
                }
            }
            return diagram;
        }


         //解析用例图（UseCase Diagram）
        private UseCaseDiagram parseUseCaseDiagram() {
            UseCaseDiagram diagram = new UseCaseDiagram();
            for (Line line : lines) {
                List<String> tokens = tokenize(line.text());
                if (tokens.isEmpty() || isDiagramDirective(tokens)) {
                    continue;
                }

                String command = tokens.get(0).toLowerCase(Locale.ROOT);
                switch (command) {
                    case "title" -> diagram.title = restAfterCommand(line.text());
                    case "actor" -> diagram.addActor(requireToken(tokens, 1, line));
                    case "usecase" -> diagram.addUseCase(requireToken(tokens, 1, line));
                    case "raw" -> diagram.rawLines.add(restAfterCommand(line.text()));
                    default -> {
                        if (line.text().contains("->") || line.text().contains("-->")) {
                            diagram.relations.add(diagram.rewriteRelation(line.text()));
                        } else {
                            throw unknownCommand(line, command);
                        }
                    }
                }
            }
            return diagram;
        }


         //文本清洗过滤：剔除前导/后导空格、空白行、以及 # 或 // 开头的注释

        private static List<Line> normalize(String source) {
            List<Line> result = new ArrayList<>();
            String[] rawLines = source.split("\\R"); // 兼容多平台的换行符切分
            for (int i = 0; i < rawLines.length; i++) {
                String trimmed = rawLines[i].trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                    continue;
                }
                result.add(new Line(i + 1, trimmed)); // 记录原始行号以便精准报错
            }
            return result;
        }


         //图表类型字符串转换器

        private static DiagramType parseDiagramType(String value, int lineNumber) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "class" -> DiagramType.CLASS;
                case "sequence" -> DiagramType.SEQUENCE;
                case "usecase", "use-case" -> DiagramType.USECASE;
                default -> throw new IllegalArgumentException("Unsupported diagram type at line " + lineNumber + ": " + value);
            };
        }

        private static boolean isDiagramDirective(List<String> tokens) {
            return !tokens.isEmpty() && "diagram:".equalsIgnoreCase(tokens.get(0));
        }


         //校验：确保当前行有足够数量的关键字参数
        private static String requireToken(List<String> tokens, int index, Line line) {
            if (tokens.size() <= index) {
                throw new IllegalArgumentException("Missing argument at line " + line.number() + ": " + line.text());
            }
            return tokens.get(index);
        }


         //裁剪提取命令后面的所有剩余文本（去外层双引号）
        private static String restAfterCommand(String text) {
            int firstSpace = text.indexOf(' ');
            if (firstSpace < 0 || firstSpace == text.length() - 1) {
                return "";
            }
            return stripWrappingQuotes(text.substring(firstSpace + 1).trim());
        }


         //将指定索引之后的所有 Token 用空格重新拼接起来

        private static String restAfterTokens(List<String> tokens, int start) {
            if (tokens.size() <= start) {
                return "";
            }
            return String.join(" ", tokens.subList(start, tokens.size()));
        }

        private static IllegalArgumentException unknownCommand(Line line, String command) {
            return new IllegalArgumentException("Unknown command at line " + line.number() + " (" + command + "): " + line.text());
        }
    }


     //带着行号的行数据模型（Java 16+ Record 特性）
    private record Line(int number, String text) {
    }


     //类图的具体数据结构与渲染实现

    private static final class ClassDiagram implements Diagram {
        private String title;
        private final Map<String, Classifier> classifiers = new LinkedHashMap<>(); // 有序 Map 保证类定义的顺序不变
        private final List<String> relations = new ArrayList<>();
        private final List<String> rawLines = new ArrayList<>();

        private void addClassifier(String kind, String name) {
            classifiers.putIfAbsent(name, new Classifier(kind, name));
        }

        private void addMember(String className, String member) {
            classifiers.computeIfAbsent(className, name -> new Classifier("class", name)).members.add(member);
        }

        @Override
        public String render() {
            StringBuilder builder = new StringBuilder();
            builder.append("@startuml\n");
            appendTitle(builder, title);
            builder.append("skinparam classAttributeIconSize 0\n\n"); // 禁用加减号图标，直接显示文本 +-

            // 循环拼接每个类及其内部 Field/Method 成员
            for (Classifier classifier : classifiers.values()) {
                builder.append(classifier.kind()).append(' ').append(quoteIfNeeded(classifier.name()));
                if (classifier.members().isEmpty()) {
                    builder.append('\n');
                } else {
                    builder.append(" {\n");
                    for (String member : classifier.members()) {
                        builder.append("  ").append(member).append('\n');
                    }
                    builder.append("}\n");
                }
            }

            appendLines(builder, relations);
            appendLines(builder, rawLines);
            builder.append("@enduml\n");
            return builder.toString();
        }
    }


     //类的包装数据结构
    private record Classifier(String kind, String name, List<String> members) {
        private Classifier(String kind, String name) {
            this(kind, name, new ArrayList<>());
        }
    }


     //时序图的简单扁平化数据结构与渲染实现

    private static final class SimpleDiagram implements Diagram {
        private String title;
        private final String type;
        private final List<String> lines = new ArrayList<>();

        private SimpleDiagram(String type) {
            this.type = type;
        }

        @Override
        public String render() {
            StringBuilder builder = new StringBuilder();
            builder.append("@startuml\n");
            appendTitle(builder, title);
            if ("sequence".equals(type)) {
                builder.append("autonumber\n\n"); // 自动打开时序图步骤编号
            }
            appendLines(builder, lines);
            builder.append("@enduml\n");
            return builder.toString();
        }
    }


     //用例图的数据结构与渲染实现
    private static final class UseCaseDiagram implements Diagram {
        private String title;
        private final Map<String, String> actors = new LinkedHashMap<>();
        private final Map<String, String> useCases = new LinkedHashMap<>();
        private final List<String> relations = new ArrayList<>();
        private final List<String> rawLines = new ArrayList<>();

        private void addActor(String name) {
            actors.putIfAbsent(name, alias("actor", name));
        }

        private void addUseCase(String name) {
            useCases.putIfAbsent(name, alias("usecase", name));
        }


         //关系改写：将连线关系中的自然语言名字自动替换成 PlantUML 内部别名（Alias）

        private String rewriteRelation(String text) {
            String rewritten = text;
            for (Map.Entry<String, String> entry : actors.entrySet()) {
                rewritten = replaceWord(rewritten, entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, String> entry : useCases.entrySet()) {
                rewritten = replaceWord(rewritten, entry.getKey(), entry.getValue());
            }
            return rewritten;
        }

        @Override
        public String render() {
            StringBuilder builder = new StringBuilder();
            builder.append("@startuml\n");
            builder.append("left to right direction\n"); // 设定用例图默认自左向右排版
            appendTitle(builder, title);

            // 渲染角色别名定义
            for (Map.Entry<String, String> entry : actors.entrySet()) {
                builder.append("actor ").append(quote(entry.getKey())).append(" as ").append(entry.getValue()).append('\n');
            }
            // 渲染用例别名定义
            for (Map.Entry<String, String> entry : useCases.entrySet()) {
                builder.append("usecase ").append(quote(entry.getKey())).append(" as ").append(entry.getValue()).append('\n');
            }

            appendLines(builder, relations);
            appendLines(builder, rawLines);
            builder.append("@enduml\n");
            return builder.toString();
        }
    }

    //核心高阶词法分析：支持按空格切分，且完美相容双引号内带有空格的复杂文本

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false; // 状态标记：当前是否处于双引号内部

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                inQuote = !inQuote; // 撞到双引号，翻转状态标志位
                continue;
            }
            // 如果遇到空格且不在双引号内部，说明一个 Token 收集完毕
            if (Character.isWhitespace(c) && !inQuote) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }

        if (inQuote) {
            throw new IllegalArgumentException("Unclosed quote in line: " + text); // 抛出异常：有未闭合的双引号
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static void appendTitle(StringBuilder builder, String title) {
        if (title != null && !title.isBlank()) {
            builder.append("title ").append(title).append("\n\n");
        }
    }

    private static void appendLines(StringBuilder builder, List<String> lines) {
        if (!lines.isEmpty()) {
            builder.append('\n');
            for (String line : lines) {
                builder.append(line).append('\n');
            }
        }
    }

    //安全过滤：若名字不满足标准的变量命名正则表达式，则强行加上双引号包裹

    private static String quoteIfNeeded(String name) {
        if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return name;
        }
        return quote(name);
    }

    private static String quote(String text) {
        return "\"" + text.replace("\"", "\\\"") + "\"";
    }


     //脱壳动作：剥离掉字符串首尾最外层包裹的双引号

    private static String stripWrappingQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }


     //别名生成器：将非字母数字的特殊符号全部用下划线 _ 代替（保证 PlantUML 内部标示符合法）

    private static String alias(String prefix, String value) {
        StringBuilder builder = new StringBuilder(prefix).append('_');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }


     //正则精细化替换：利用正则断言，做到只替换“独立的完整单词”，防止误伤部分重名的类

    private static String replaceWord(String text, String target, String replacement) {
        return text.replaceAll("(?<![A-Za-z0-9_])" + java.util.regex.Pattern.quote(target) + "(?![A-Za-z0-9_])", replacement);
    }
}