
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
	# Stage 1: Make a jar of the classes needed for peer.
	mkdir -p ./bfd-swt-peer
	rm -f manifest.md
	echo "Name: BetterFileDialogPeer" >> manifest.md
	echo "Main-Class: org.kwalsh.BetterFileDialogPeer" >> manifest.md
	echo "Implementation-Build-Date: $(DATE)" >> manifest.md
	echo "Implementation-Version: $(VERSION)" >> manifest.md
	echo "Implementation-Vendor: Kevin Walsh" >> manifest.md
	echo "Built-By: $(USER)" >> manifest.md
	jar cfm bfd-swt-peer/bfd-peer.jar manifest.md org/kwalsh/*.class
	cp swt-linux.jar swt-macos.jar swt-windows.jar ./bfd-swt-peer/
	# Stage 2: Make client jar
	rm -f manifest.md
	echo "Name: BetterFileDialog" >> manifest.md
	echo "Implementation-Build-Date: $(DATE)" >> manifest.md
	echo "Implementation-Version: $(VERSION)" >> manifest.md
	echo "Implementation-Vendor: Kevin Walsh" >> manifest.md
	echo "Built-By: $(USER)" >> manifest.md
	jar cfm betterfiledialog.jar manifest.md org/kwalsh/*.class bfd-swt-peer LICENSE

.PHONY: demo
demo:
	javac -d . -cp betterfiledialog.jar ./src/demo/*.java
	@echo "Now try:"
	@echo "   java -cp etterfiledialog.jar:. ShortExample"
	@echo "Or:"
	@echo "   java -cp etterfiledialog.jar:. Example"

.PHONY: clean
clean:
	rm -rf org
	rm -rf bfd-swt-peer
	rm -f manifest.mf

.PHONY: distclean
distclean:
	rm -rf org
	rm -rf bfd-swt-peer
	rm -f manifest.mf
	rm -f betterfiledialog.jar
