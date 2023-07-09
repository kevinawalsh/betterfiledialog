
DATE:=$(shell date)
VERSION:=$(shell grep "version =" src/org/kwalsh/BetterFileDialog.java  | sed 's/.*"\(.*\)\".*/\1/')
OS:=$(uname)

ifeq ($(OS), Darwin)
  SWT:=swt-macos.jar
else ifeq ($(OS), Linux)
  SWT:=swt-linux.jar
else
  SWT:=swt-windows.jar
endif

.PHONY: all
all: jar

.PHONY: jar
jar:
	javac -d . -cp $(SWT) ./src/org/kwalsh/*.java
	rm -f manifest.md
	echo "Name: BetterFileDialog" >> manifest.md
	echo "Implementation-Build-Date: $(DATE)" >> manifest.md
	echo "Implementation-Version: $(VERSION)" >> manifest.md
	echo "Implementation-Vendor: Kevin Walsh" >> manifest.md
	echo "Built-By: $(USER)" >> manifest.md
	jar cfm betterfiledialog.jar manifest.md org/kwalsh/*.class LICENSE

.PHONY: demo
demo:
	javac -d . -cp $(SWT):betterfiledialog.jar ./src/demo/*.java
	@echo "Now try:"
	@echo "   java -cp $(SWT):betterfiledialog.jar:. ShortExample"
	@echo "Or:"
	@echo "   java -cp $(SWT):betterfiledialog.jar:. Example"

.PHONY: clean
clean:
	rm -rf org
	rm -f manifest.mf

.PHONY: distclean
distclean:
	rm -rf org
	rm -f manifest.mf
	rm -f betterfiledialog.jar
