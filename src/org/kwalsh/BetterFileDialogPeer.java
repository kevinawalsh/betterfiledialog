package org.kwalsh;

import java.io.File;
import java.util.ArrayList;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

// TODO:
//  test on all platforms
//  try System.setProperty("apple.awt.UIElement", "true");
//  try System.setProperty("apple.awt.headless", "true");
//  try System.setProperty("java.awt.headless", "true");
//  x fix classpath and bin/java paths
//  better jar packaging

public class BetterFileDialogPeer {

  static int traceLevel = 0;

  static boolean isMacOS;
  static boolean isLinux;
  static boolean isWindows;

  static Display swtDisplay;
  static Shell swtShell;

  static final int MODE_OPEN = 1;
  static final int MODE_SAVE = 2;
  static final int MODE_MULTI = 3;
  static final int MODE_DIR = 4;

  static int mode = -1;

  static String appName = "Selection Dialog";
  static String title;
  static int xloc = -1, yloc = -1;
  static String initialPath;
  static String initialDir;
  static String suggestedFileName;
  static ArrayList<BetterFileDialog.Filter> filters = new ArrayList<>();

  // Entry point for SWT-based child process. Command-line arguments are:
  // --appname name
  // --prompt openfile|openfiles|savefile|pickdir
  // --loc x,y
  // --title title 
  // --path initialPath
  // --filter name:ext1,ext2,ext3,...
  // --debug level
  public static void main(String[] args) {
    try { parseArgs(args); }
    catch (Throwable e) { die(e); }

    if (mode < 0)
      die("missing prompt argument");

    trace(1, "Executing");

    try { run(); }
    catch (Throwable e) { die(e); }

    trace(1, "Exiting");

    System.out.println("EXIT");
    System.exit(0);
  }

  static void cleanup() {
    if (swtShell != null && !swtShell.isDisposed()) {
      try { swtShell.close(); }
      catch (Throwable t) { t.printStackTrace(); }
      try { swtShell.dispose(); }
      catch (Throwable t) { t.printStackTrace(); }
    }
    if (swtDisplay != null && !swtDisplay.isDisposed()) {
      try { swtDisplay.dispose(); }
      catch (Throwable t) { t.printStackTrace(); }
    }
  }

  static void die(String msg) { die(msg, null); }
  static void die(Throwable e) { die(e.getMessage(), e); }
  static void die(String msg, Throwable e) {
    System.out.println("ERROR: " + msg);
    if (e != null)
      e.printStackTrace();
    cleanup();
    System.out.println("EXIT");
    System.exit(1);
  }

  static void parseArgs(String[] args) throws Exception {
    for (int i = 0; i < args.length; i += 2) {
      String arg = args[i];
      String param = args[i+1];
      if (arg.equals("--appName")) {
        appName = param;
      } else if (arg.equals("--prompt")) {
        if (param.equalsIgnoreCase("openfile"))
          mode = MODE_OPEN;
        else if (param.equalsIgnoreCase("savefile"))
          mode = MODE_SAVE;
        else if (param.equalsIgnoreCase("openfiles"))
          mode = MODE_MULTI;
        else if (param.equalsIgnoreCase("pickdir"))
          mode = MODE_DIR;
        else
          throw new Exception("malformed arguments (bad prompt)");
      } else if (arg.equals("--title")) {
        title = param;
      } else if (arg.equals("--loc")) {
        int idx = param.indexOf(",");
        xloc = Integer.parseInt(param.substring(0, idx));
        yloc = Integer.parseInt(param.substring(idx+1));
      } else if (arg.equals("--path")) {
        initialPath = param;
      } else if (arg.equals("--filter")) {
        int idx = param.indexOf(":");
        String name = param.substring(0, idx);
        String[] exts = param.substring(idx+1).split(",");
        filters.add(new BetterFileDialog.Filter(name, exts));
      } else if (arg.equals("--debug")) {
        traceLevel = Integer.parseInt(param);
      }
    }

    initialDir = toDir(initialPath);
    suggestedFileName = toName(initialPath);
  }

  static void run() {

    isMacOS = System.getProperty("os.name").toLowerCase().startsWith("mac");
    isLinux = System.getProperty("os.name").toLowerCase().startsWith("linux");
    isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
   
    long pid = ProcessHandle.current().pid();

    trace(2, "Running as process " + pid +" on" +
        (isMacOS ? " MacOS" : "") +
        (isLinux ? " Linux" : "") +
        (isWindows ? " Windows" : ""));

    trace(1, "Checking jvm options");
    String env = System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + pid);
    if (isMacOS && !"1".equals(env)) {
      System.out.println("ERROR: JVM on MacOS must" +
          "be executed with the -XstartOnFirstThread command-line option.\n");
      die("Bad JVM configuration");
    }

    trace(1, "Preparing SWT display");
    Display.setAppName(appName);
    swtDisplay = new Display();

    trace(1, "Scheduling continuation");
    Thread.currentThread().setName("Main Thread");
    swtDisplay.asyncExec(() -> {
      try {
        Thread.currentThread().setName("SWT Event Thread");
        trace(1, "Executing continuation");

        process();

        cleanup();
      } catch (Throwable e) {
        die(e);
      }
    });

    trace(1, "Starting event loop");
    while (!swtDisplay.isDisposed())
    {
      if (!swtDisplay.readAndDispatch())
        swtDisplay.sleep();
    }
    trace(1, "Event loop terminated");
  }

  static void process() throws Exception {
    swtShell = new Shell(swtDisplay, SWT.ON_TOP);

    if (xloc >= 0 && yloc >= 0) {
      trace(3, "Positioning at ("+xloc+","+yloc+")");
    } else {
      Rectangle b = swtDisplay.getPrimaryMonitor().getBounds();
      xloc = b.x + b.width/2;
      yloc = b.y + b.height/2;
      trace(3, "Centering on screen at ("+xloc+","+yloc+")");
    }

    if (isWindows) {
      // On Windows, the FileDialog positions itself so the top left corner is
      // on the shell, so we move the shell a bit to the left and up.
      xloc -= 250;
      yloc -= 150;
    }

    if (traceLevel > 1) {
      Label x = new Label(swtShell, SWT.BORDER);
      x.setSize(300,30);
      x.setLocation(50, 50);
      x.setText("SWT Shell for " + appName);
      if (traceLevel < 3)
        swtShell.setAlpha(150);
      swtShell.setSize(400, 100);
      swtShell.setLocation(xloc - 200, yloc - 50);
    } else {
      if (isLinux)
        swtShell.setSize(2, 2); // On linux, Gtk prints console messages for size < 2
      else
        swtShell.setSize(0, 0);
      swtShell.setLocation(xloc, yloc);
      // swtShell.setVisible(false); // no effect on Linux
      // swtShell.setAlpha(0); // works on Linux
    }

    swtShell.open(); // this is necessary for modal behavior of SWT to work properly

    if (mode == MODE_OPEN) pickFile(SWT.OPEN);
    else if (mode == MODE_MULTI) pickFile(SWT.MULTI);
    else if (mode == MODE_SAVE) pickFile(SWT.SAVE);
    else if (mode == MODE_DIR) pickDirectory();
  }

  static void pickDirectory() {
    DirectoryDialog dialog = new DirectoryDialog(swtShell);
    if (title != null) {
      if (isWindows) {
        // Windows uses text as title but ignores the message
        dialog.setText(title);
        dialog.setMessage("Select a directory"); // ignored
      } else if (isLinux) {
        // Linux uses text as title and shows message at bottom of dialog
        dialog.setText(title);
        dialog.setMessage("Select a directory");
      } else {
        // MacOS uses message as title but ignores text
        dialog.setText("Select a directory"); // ignored
        dialog.setMessage(title);
      }
    }
    if (initialDir != null)
      dialog.setFilterPath(initialDir);

    String ret = dialog.open();

    trace(1, "Result=" + ret);
    trace(1, "FilterPath=" + dialog.getFilterPath());

    System.out.println("RESULT: " + ret);
  }

  static void pickFile(int style) {
    FileDialog dialog = new FileDialog(swtShell, style);
    if (title != null)
      dialog.setText(title);
    if (initialDir != null)
      dialog.setFilterPath(initialDir);

    int ct = filters.size();
    if (ct > 0) {
      String[] e = new String[ct];
      String[] n = new String[ct];
      for (int i = 0; i < ct; i ++) {
        e[i] = filters.get(i).getExtensions();
        n[i] = filters.get(i).getDescription(); // would name be better for UI?
      }
      dialog.setFilterExtensions(e);
      dialog.setFilterNames(n);
      dialog.setFilterIndex(0);
    } else if (isWindows) {
      // Windows fix: with no filters, Windows adds a poorly-formatted filter
      // named "*.*" with no description. Use the ANY_FILTER instead in this
      // case.
      dialog.setFilterExtensions(new String[] {
        BetterFileDialog.ANY_FILTER.getExtensions() });
      dialog.setFilterNames(new String[] { 
        BetterFileDialog.ANY_FILTER.getDescription() });
      dialog.setFilterIndex(0);
    }

    if (suggestedFileName != null) {
      if (style == SWT.SAVE) {
        dialog.setFileName(suggestedFileName);
      } else {
        // File must exist, otherwise initialDir isn't obeyed properly.
        File f = new File(initialDir, suggestedFileName);
        if (f.exists())
          dialog.setFileName(suggestedFileName);
      }
    }

    if (mode == MODE_SAVE) {
      // We set SWT's overwrite-checking to false, so that we can add the default
      // extension, if needed, before doing any such checking.
      dialog.setOverwrite(false);
      // On some platforms, like MacOS 10.15 and higher, setting
      // overwrite-checking to false does not work, so SWT might still check for
      // overwrites.
      if (dialog.getOverwrite())
        System.out.println("STATUS: checked overwrite");
    }

    String ret = dialog.open();

    if (traceLevel > 0) {
      trace(1, "Result=" + ret);
      trace(1, "FilterPath=" + dialog.getFilterPath());
      trace(1, "FileName=" + dialog.getFileName());
      String[] a = dialog.getFileNames();
      if (a == null) {
        trace(1, "FileNames=(null)");
      } else if (a.length == 0) {
        trace(1, "FileNames=(empty array)");
      } else {
        for (int i = 0; i < a.length; i++)
        trace(1, "FileNames["+i+"]="+a[i]);
      }
    }
    
    if (ret == null) {
      System.out.println("CANCELED");
      return;
    }

    if ("".equals(ret)) { // empty filename seems like a bad idea
      System.out.println("CANCELED");
      return;
    }

    // Check extension for save-file result.
    if (mode == MODE_SAVE) {
      // Suppose there are two filters: "*.jpg;*.jpeg" and "*.png"
      // and the user selects the "*.jpg;*.jpeg" filter.
      // If the user enters "foo.jpg", we leave it alone, as it
      // matches the chosen filter.
      // If the user enters "foo.png", we leave it alone, as it
      // still matches an acceptable filter.
      // If the user enters "foo", we change it to "foo.jpg"
      // because that's the default extension for the filter they chose.
      if (!BetterFileDialog.matchesFilterExtension(ret, filters)) {
        // This happens only if there are some filters, and none of the filters
        // have wildcards, so we can be assured there is a default extension for
        // whichever filter was chosen by the user.
        int idx = dialog.getFilterIndex();
        // BUG: On (some) Linux platforms, getFilterIndex() does not work. We
        // use the first filter in that case.
        if (idx < 0 || idx >= filters.size()) // is this possible?
          idx = 0;
        String ext = filters.get(idx).getDefaultExtension();
        System.out.println("STATUS: suggest extension: " + ext);
      }
    }

    // Send results
    if (mode == MODE_SAVE) {
      System.out.println("RESULT: " + ret);
    } else if (mode == MODE_OPEN) {
      System.out.println("RESULT: " + ret);
    } else if (mode == MODE_MULTI) {
      String[] names = dialog.getFileNames();
      if (names.length == 0) { // empty array seems invalid?
        System.out.println("CANCELED");
      } else {
        System.out.println("RESULT DIR: " + dialog.getFilterPath());
        System.out.println("RESULT COUNT: " + names.length);
        for (String name : names)
          System.out.println("RESULT: " + name);
      }
    }

  }

  static String toDir(File path) {
    if (path == null)
      return null;
    if (path.isDirectory())
      return path.getPath();
    File parent = path.getParentFile();
    if (parent != null)
      return parent.getPath();
    return null;
  }

  static String toName(File path) {
    if (path == null)
      return null;
    if (!path.isDirectory())
      return path.getName();
    return null;
  }

  static String toDir(String path) {
    if (path == null)
      return null;
    if (path.endsWith(File.separator))
      return path;
    return toDir(new File(path));
  }

  static String toName(String path) {
    if (path == null)
      return null;
    if (path.endsWith(File.separator))
      return null;
    return toName(new File(path));
  }

  protected static void trace(int lvl, String msg) {
    if (traceLevel >= lvl)
      System.out.println("* " + msg);
  }


}
