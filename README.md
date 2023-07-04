# BetterFileDialog
Better file load/save dialogs for Java AWT and Swing applications.

It's 2023, and there is still no decent, cross-platform, and reliable way for
Java AWT or Swing applications to display a file load/save dialog or a directory
picker dialog. This little module is meant as a workaround, using Eclipse SWT
under the covers, with a light weight layer on top to allow the SWT dialogs to
be accessed from AWT and Swing applications, and smoothing over lots of SWT
quirks and bugs.

For any AWT or Swing based Java application, `BetterFileDialog` takes the place
of `java.awt.FileDialog` and `javax.swing.JFileChooser`. It is simpler to use,
more flexible, provides a better user interfaces, and works more consistently
across platforms. It provides:

* An "Open File" dialog, with selectable filters based on file extensions, along
  with similar "Open Files" and "Save File" dialog.

* A "Select Directory" dialog.

All of these use platform-native dialogs on Linux, MacOS, and Windows, and all
supporting platform-native keyboard navigation and shortcuts, platform-native
drag-and-drop, and platform-native icons, fonts, and styling.

## Usage

To use `BetterFileDialog` in your AWT or Swing based java application:

1. Import `org.kwalsh.BetterFileDialog` in your code.

2. In your `main()` function, call `BetterFileDialog.init(a, b, c)` early in
   your program, before doing any AWT or Swing code. The `init()` function does
   not return. Instead, it takes three (optional) `Runnable` parameters. The
   first, `a`, will be executed asynchronously on the AWT/Swing even thread. The
   second, `b`, will be executed on the SWT event thread. The third, `c`, will
   be executed on a newly created thread. 

3. Call `BetterFileDialog.openFile()`, `BetterFileDialog.openFiles()`,
   `BetterFileDialog.saveFile()`, or `BetterFileDialog.pickDirectory()` whenever
   you like, from either the AWT/Swing event thread, or from some other
   unrelated thread. You can pass in parameters to customize the dialog's
   position, title, initial directory, initial file name, and user-selectable
   filters.

See the API below for details. Here's a short but complete example using
`BetterFileDialog` from Swing:

```java
    // Step 1:  Import the class
    import org.kwalsh.BetterFileDialog; 
    import java.awt.*;
    
    class ShortExample {
    
      static String dir = System.getProperty("user.home");
      
      public static void main(String[] args) {
        if (args.length > 0)
          dir = args[0];
    
        // Step 2: Initialize it, early in your main function
        BetterFileDialog.init(() -> demo(), null, null);  
      }
    
      public static void demo() {
        final Dialog dlg = new Dialog((Frame)null, "Demo", true);
        dlg.setLayout(null);
        dlg.setSize(300, 300);
        Button b = new Button("Click Me");
        b.setBounds(100, 100, 100, 100);
        b.addActionListener(e -> click(dlg));
        dlg.add(b);
        dlg.setVisible(true);
      }
    
      public static void click(Dialog parent) {
   
        // Step 3: When you need a popup, call just one function
        String choice = BetterFileDialog.openFile(parent,
            "Pick Your Best Pic", dir,
            BetterFileDialog.JPG_FILTER,
            BetterFileDialog.PNG_FILTER,
            BetterFileDialog.ANY_FILTER);

        if (choice == null)
          System.out.println("Cancelled, goodbye!");
        else
          System.out.println("You picked: " + choice);
      }
    }
```

## Requirements, Compiling and Running Client Applications

To compile and run your application:

1. Put `betterfiledialog.jar` and one of the three SWT files (`swt-linux.jar`,
   `swt-macos.jar`, or `swt-windows.jar`) in your `CLASSPATH`.

2. On MacOS, add `-XstartOnMainThread` as a JVM option when starting your app.

`BetterFileDialog` was designed for Java 17, though it likely works on later
versions and could likely be compiled for earlier versions. It uses Eclipse SWT
under the cover.

To compile `BetterFileDialog` itself, use the provided `Makefile` by just running `cd
src && make` on Linux. For other platforms, adjust the `Makefile`, or just run
the `javac` and `jar` commands from the `Makefile` manually.

## API

```java

public class BetterFileDialog {
  // For debugging, zero means no printing, higher values yield more output.
  public static int traceLevel = 0;

  /* Initialize the BetterFileDialog module.
   * This must be called from the main thread, i.e. the thread which runs
   * main(), and it should probably be called very early before any AWT/Swing or
   * SWT code executes. On MacOS, the jvm must have been started with the
   * -XstartOnMainThread option.
   * This function does not return, but will (asynchronously) run the three given
   * tasks.
   * @param awtTask - work to be executed on the AWT/Swing thread.
   * @param swtTask - work to be executed on the SWT thread.
   * @param mainTask - work to be executed on a newly-created thread.
   */
  public static void init(Runnable awtTask, Runnable swtTask, Runnable mainTask);

  /**
   * Pop up an open-file dialog and return the selected file.
   * @param parent - used for dialog positioning on Windows and Linux. May be
   *    null. On MacOS, the dialog is always centered on the screen.
   * @param title - title for the dialog, or null for system default title.
   * @param initialPath - sets initial directory and suggested filename. May be
   *   null. For MacOS, only the filename is used, and the initial directory is
   *   always chosen by the platform based on recent accesses. 
   *   If the path is to an existing directory, or if getParentFile() is not
   *   null, this is used for the initial directory (or some ancestor if that
   *   directory isn't accessible). Otherwise, a platform-specific default is
   *   used as the initial directory.
   *   If the path is not to a directory, then getName() is used for the initial
   *   filename. Otherwise, a platform-specific default or emptystring is used
   *   as the initial filename.
   * @param filters - list of zero or more filters user can choose from. The
   *    first one is selected by default.
   * @return the user's chosen file, or null if canceled by user.
   */
  public static File openFile(Component parent, String title,
      File initialPath, Filter... filters);

  // initialPath - May be null. If it contains a separator, then the trailing
  // part (if non-blank) is used as the suggested name, and the rest is used for
  // the initial directory. Otherwise, the entire initialPath is used for the
  // suggested name.
  public static String openFile(Component parent, String title,
      String initialPath, Filter... filters);

  /**
   * Same as openFile(), but allows selecting multiple files.
   * @return the list of one or more chosen files, or null if canceled by user.
   */
  public static File[] openFiles(Component parent, String title,
      File initialPath, Filter... filters);

  public static String[] openFiles(Component parent, String title,
      String initialPath, Filter... filters);

  /**
   * Pop up an save-file dialog and return the selected file.
   * @param parent - used for dialog positioning on Windows and Linux. May be
   *    null. On MacOS, the dialog is always centered on the screen.
   * @param title - title for the dialog, or null for system default title.
   * @param initialPath - same as for openFile().
   * @param filters - list of zero or more filters user can choosse from. The
   *    first one is selected by default.
   * @return the user's chosen file, or null if canceled by user. The chosen
   *    file is then validated against the filter list and, if it does not
   *    match, then a default extension is added based on the filter chosen by
   *    the user. (BUG: On (some) Linux platforms, the first fitler is used for
   *    this case, not the one chosen by the user.) If the file exists but is
   *    not writeable or is a directory, an error is shown. If the file exists
   *    and is writable, the user is warned about overwriting the file with a
   *    chance to cancel.
   */
  public static File saveFile(Component parent, String title,
      File initialPath, Filter... filters);

  public static String saveFile(Component parent, String title,
      String initialPath, Filter... filters);

  /**
   * Pop up a select-directory dialog and return the selected directory.
   * @param parent - used for dialog positioning on Windows and Linux. May be
   *    null. On MacOS, the dialog is always centered on the screen.
   * @param title - title for the dialog, or null for system default title.
   * @param initialDir - set initial directory for Windows and Linux. On MacOS,
   *    a system "last accessed" default is used. May be null.
   * @return the user's chosen directory, or null if canceled by user.
   */
  public static File pickDir(Component parent, String title, File initialDir);
  
  public static String pickDir(Component parent, String title, String initialDir);


  // Some pre-defined filters, for convenience.
  public static final Filter ANY_FILTER = new Filter("All Files", "*");
  public static final Filter JPG_FILTER = new Filter("JPEG Images", "jpg", "jpeg");
  public static final Filter PNG_FILTER = new Filter("PNG Images", "png");
  public static final Filter IMAGE_FILTER = new Filter("Images", "jpg", "jpeg", "png");
  public static final Filter TXT_FILTER = new Filter("Plain Text Files", "txt");
  public static final Filter XML_FILTER = new Filter("XML Files", "xml");

  /**
   * Filter relies on file extensions to determine acceptability for display.
   * This is the only type of filtering supported, as it is the only filtering
   * that works across the three supported platforms.
   */
  public static final class Filter 
    extends javax.swing.filechooser.FileFilter
    implements java.io.FileFilter {

    /**
     * Construct a Filter that accepts files with one of the given extensions,
     * or any directory.
     * @param name - a name for this filter, e.g. "Image Files".
     * @param extension - one or more extensions, e.g. "jpg", "png", "*". A
     * leading "." or "*.", if present, is removed.
     *
     * The extensions should not include wildcards or special characters, except
     * "*" may be used by itself to match all file extensions. No extension
     * should be the null or empty string.
     *
     * If no extensions are given, or if "*" is given as an allowed extension,
     * then all files will be accepted.
     *
     * For extensions of 4 characters or less, matching is case-insensitive on
     * all platforms, regardless of whether the underlying system uses case
     * sensitive or case insenstive file names. For longer file extensions,
     * case sensitivity depends on the platform.
     */
    public Filter(String name, String... extension);

    // Return the name of this filter, for example, "Image Files".
    public String getName();

    // Return a description of this filter, for example,
    // "Image Files (*.png, *.jpg, *.jpeg)".
    @Override
    public String getDescription();

    // Return a semi-colon delineated list of extensions, for example,
    // "*.jpg;*.png;*.jpeg;*.JPG;*.Jpg;*.JPg;..." This includes permutatons of
    // upper/lower case for extensions with four characters or less.
    public String getExtensions();

    // Return whether this filter accepts all filenames.
    public boolean isWildcard();

    // Return the default extension, i.e. the first non-wildcard extension in
    // the list of accepted extensions.
    public String getDefaultExtension();

    /**
     * Check if a given directory/file pair matches one of the allowed
     * extensions.
     * @param dir - The directory in which the file was found.
     * @param name - The name of the file.
     * @return true iff the name matches one fo the allowed extensions.
     * NOTE: This isn't used by SWT, but can be used, for example, to check if a
     * result matches a desired filter.
     */
    public boolean accept(File dir, String name);

    /**
     * Check if a given file or directory matches one of the allowed
     * extensions.
     * @param path - The file or directory.
     * @return true iff the name matches one fo the allowed extensions.
     * NOTE: This isn't used by SWT, but can be used, for example, to check if a
     * result matches a desired filter. Matching is case-insensitive for all
     * file extensions, regardless of length.
     */
    public boolean accept(File path);
    public boolean accept(String path);

  } // end of Filter

} // end of BetterFileDialog
```

## Really? Why not just use ... 

* **Java's Swing support for load/save dialogs, using `javax.swing.JFileChooser`?**
  The UI looks terrible out of the box, far removed from platform-native
  dialogs, and there is no keyboard navigation. Swing has not been meaningfully
  improved in many years.

* **Java's basic `java.awt.FileDialog`?** This looks better, especially on MacOS and Linux
  platforms, providing access to native load/save dialogs. But on Windows
  platforms, `java.awt.FileDialog` does not support filename filters
  (`getFilenameFilter()`, `setFilenameFilter()`) at all, and on MacOS and Linux
  it can't do choosable filename filters. The dialog positioning is weird on
  Linux and Windows, you can't pick an initial directory on MacOS, and it
  doesn't support picking directories on all platforms. Additionally, in 2023,
  the AWT ecosystem appears to have been essentially abandoned, so it is
  unlikely this approach will be improved in future either. 

* **JNI access to the underlying platform?** I tried this. See
  [`xfiledialog`](https://github.com/kevinawalsh/xfiledialog) for my attempt on
  Windows, falling back to AWT on Linux and MacOS. I gave up after realizing
  that AWT wasn't good enough on MacOS or Linux, so I'd need to make native JNI
  packages for all three platforms.

* **JavaFX's dialogs?** The required JavaFX libraries and jar files add up to
  over 30MB, more than the JVM and all the typical standard modules. The steps
  for compiling, packaging, and deploying JavaFX are quite complicated. And
  JavaFX appears to be essentially abandoned at this point in 2023, with most
  documentation online hopelessly outdated.

* **SWT dialogs, the Eclipse Foundation's Standard Widget Toolkit?** These are
  pretty good, and SWT underlies `BetterFileDialog`. But by itself, it's not
  easy to use `SWT` from within an AWT or Swing application. It also has lots of
  cross-platform inconsistencies and quirks. `BetterFileDialog` works around all
  that, providing a simple no-fuss interface to use `SWT`.

## Known Issues

* On MacOS, messages are unavoidably printed to the console, such as:

      2023-06-30 20:53:24.616 java[28668:221731] +[CATransaction synchronize] called within transaction
      2023-06-30 20:53:40.284 java[28668:221731] *** Assertion failure in -[AWTWindow_Normal _changeJustMain], NSWindow.m:14356

* On Linux, the popup dialogs are system-modal, rather than
  application-modal as most Linux modals would normally be.

* File extensions longer than 4 characters are not properly case-insensitive
  on all platforms.

* On Linux, if the user enters a filename for save-file that does not match
  any filters, then the first filter's extension is added, rather than the
  currently-selected filter.

* We warn on overwrite for save-file for all platforms, because this can't
  reliably be disabled on some platforms. On MacOS 10.15 and later, there can be
  some cases where two somewhat contradictory warnings will occur, one from the
  system, and one from BetterFileDialog after changing the name to add a proper
  extension (but usually, MacOS adds a reasonable extension already, so in the
  common case the user sees only the MacOS warning).

## Credits and License

This project is maintained by Kevin Walsh <kwalsh@holycross.edu> and is released
under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

