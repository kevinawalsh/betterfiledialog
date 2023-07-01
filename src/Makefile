
SWT:=../swt-linux.jar
#SWT:=../swt-macos.jar
#SWT:=../swt-windows.jar


.PHONY: all
all: jar

.PHONY: jar
jar:
	javac -cp $(SWT) org/kwalsh/*.java
	jar cf ../betterfiledialog.jar org/kwalsh/*.class

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

.PHONY: distclean
distclean:
	rm -f org/kwalsh/*.class
	rm -f demo/*.class
	rm -f ../betterfiledialog.jar
