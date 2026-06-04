JAVAC := javac
JAVA := java
JAR := jar
OUT := out
BUILD := build
MAIN := main.java.com.example.umltool.UmlTool
SRC := src/main/java/com/example/umltool/UmlTool.java
.PHONY: build examples jar clean
build:
	mkdir -p $(OUT)
	$(JAVAC) -d $(OUT) $(SRC)
examples: build
	mkdir -p $(BUILD)
	$(JAVA) -cp $(OUT) $(MAIN) examples/class.txt $(BUILD)/class.puml
	$(JAVA) -cp $(OUT) $(MAIN) examples/sequence.txt $(BUILD)/sequence.puml
	$(JAVA) -cp $(OUT) $(MAIN) examples/usecase.txt $(BUILD)/usecase.puml
jar: build
	mkdir -p $(BUILD)
	$(JAR) --create --file $(BUILD)/uml-tool.jar --main-class $(MAIN) -C $(OUT) .
clean:
	rm -rf $(OUT) $(BUILD)
