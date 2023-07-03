
SWT:=../swt-linux.jar
#SWT:=../swt-macos.jar
#SWT:=../swt-windows.jar

DATE:=$(shell date)
VERSION:=$(shell grep "version =" org/kwalsh/BetterFileDialog.java  | sed 's/.*"\(.*\)\".*/\1/')

.PHONY: all
all: jar

.PHONY: jar
jar:
	javac -cp $(SWT) org/kwalsh/*.java
	rm -f manifest.md
	echo "Name: BetterFileDialog" >> manifest.md
	echo "Implementation-Build-Date: $(DATE)" >> manifest.md
	echo "Implementation-Version: $(VERSION)" >> manifest.md
	echo "Implementation-Vendor: Kevin Walsh" >> manifest.md
	echo "Built-By: $(USER)" >> manifest.md
	jar cfm ../betterfiledialog.jar manifest.md org/kwalsh/*.class

.PHONY: demo
demo:
	javac -cp $(SWT):../betterfiledialog.jar demo/*.java
	@echo "Now try:"
	@echo "   java -cp $(SWT):../betterfiledialog.jar:demo ShortExample"
	@echo "Or:"
	@echo "   java -cp $(SWT):../betterfiledialog.jar:demo Example"

.PHONY: clean
clean:
	rm -f org/kwalsh/*.class
	rm -f demo/*.class
	rm -f manifest.mf

.PHONY: distclean
distclean:
	rm -f org/kwalsh/*.class
	rm -f demo/*.class
	rm -f manifest.mf
	rm -f ../betterfiledialog.jar
