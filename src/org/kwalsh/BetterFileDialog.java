package org.kwalsh;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

// Current known issues:
//
// * On MacOS, messages are unavoidably printed to the console, such as:
//   2023-06-30 20:53:24.616 java[28668:221731] +[CATransaction synchronize] called within transaction
//   2023-06-30 20:53:40.284 java[28668:221731] *** Assertion failure in -[AWTWindow_Normal _changeJustMain], NSWindow.m:14356
//
// * On Linux, the popup dialogs are system-modal, rather than
//   application-modal as most Linux modals would normally be.
//
// * File extensions longer than 4 characters are not properly case-insensitive
//   on all platforms.
//
// * On Linux, if the user enters a filename for save-file that does not match
//   any filters, then the first filter's extension is added, rather than the
//   currently-selected filter.

public class BetterFileDialog {

  public static final String version = "1.0.2";

  // For debugging, zero means no printing, higher values yield more output.
  public static int traceLevel = 0;

  // Platform type.
  protected static boolean isMacOS;
  protected static boolean isLinux;
  protected static boolean isWindows;

  // SWT Display used for creating SWT FileDialog and DirectoryDialog modals.
  protected static Display swtDisplay;


  /**
   * Initialize the BetterFileDialog module.
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
  public static void init(Runnable awtTask, Runnable swtTask, Runnable mainTask) {

    if (traceLevel > 0) {
      System.out.println("BetterFileDialog: Initializing");
    }

    isMacOS = System.getProperty("os.name").toLowerCase().startsWith("mac");
    isLinux = System.getProperty("os.name").toLowerCase().startsWith("linux");
    isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
   
    long pid = ProcessHandle.current().pid();

    if (traceLevel > 1)
      System.out.println("BetterFileDialog: Running as process " + pid +" on" +
          (isMacOS ? " MacOS" : "") +
          (isLinux ? " Linux" : "") +
          (isWindows ? " Windows" : ""));

    if (traceLevel > 0)
      System.out.println("BetterFileDialog: Checking jvm options");
    String env = System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + pid);
    if (isMacOS && !"1".equals(env)) {
      System.err.println("BetterFileDialog: Critical error, JVM on MacOS must\n" +
          "be executed with the -XstartOnFirstThread command-line option.\n");
      boolean quit = true;
      try { quit = showFatalError(); }
      catch (Exception e) { }
      if (quit)
        System.exit(1);
    }

    if (traceLevel > 0)
      System.out.println("BetterFileDialog: Preparing SWT display");
    swtDisplay = new Display();

    if (traceLevel > 0)
      System.out.println("BetterFileDialog: Loading AWT toolkit");
		Toolkit.getDefaultToolkit();

    if (traceLevel > 0)
      System.out.println("BetterFileDialog: Starting application tasks");
    if (awtTask != null)
      EventQueue.invokeLater(awtTask);
    if (swtTask != null)
      swtDisplay.asyncExec(swtTask);
    if (mainTask != null)
      new Thread(mainTask, "BetterFileDialog-Main-Task").start();

    if (traceLevel > 0)
      System.out.println("BetterFileDialog: Starting event loop");
    while (!swtDisplay.isDisposed())
    {
      if (!swtDisplay.readAndDispatch())
        swtDisplay.sleep();
    }
    if (traceLevel > 0)
      System.out.println("BetterFileDialog: Event loop terminated");
  }

  protected static boolean showFatalError() {
    String msg = "The JVM on MacOS must be executed with"
        + " the -XstartOnFirstThread command-line option.";
    String[] options = { "Quit", "Continue Anyway" };
    int choice = JOptionPane.showOptionDialog(null, msg, "Critical Error",
        0, JOptionPane.ERROR_MESSAGE, null, options, options[0]);
    return (choice != 1);
  }

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
      File initialPath, Filter... filters) {
    BetterFileDialog dlg = new BetterFileDialog(MODE_OPEN, parent, title,
        toDir(initialPath), toName(initialPath), filters);
    dlg.doModal();
    return toFile(dlg.fileResult);
  }
  // initialPath - May be null. If it contains a separator, then the trailing
  // part (if non-blank) is used as the suggested name, and the rest is used for
  // the initial directory. Otherwise, the entire initialPath is used for the
  // suggested name.
  public static String openFile(Component parent, String title,
      String initialPath, Filter... filters) {
    BetterFileDialog dlg = new BetterFileDialog(MODE_OPEN, parent, title,
        toDir(initialPath), toName(initialPath), filters);
    dlg.doModal();
    return dlg.fileResult;
  }

  /**
   * Same as openFile(), but allows selecting multiple files.
   * @return the list of one or more chosen files, or null if canceled by user.
   */
  public static File[] openFiles(Component parent, String title,
      File initialPath, Filter... filters) {
    BetterFileDialog dlg = new BetterFileDialog(MODE_MULTI, parent, title,
        toDir(initialPath), toName(initialPath), filters);
    dlg.doModal();
    int n = dlg.multiResult == null ? 0 : dlg.multiResult.length;
    if (n == 0)
      return null;
    File[] ret = new File[n];
    for (int i = 0; i < n; i++) {
      // Quirk: multiResult gives relative file names only, we need
      // to add the directory part.
      ret[i] = new File(dlg.dirResult, dlg.multiResult[i]); // should not be null
    }
    return ret;
  }
  public static String[] openFiles(Component parent, String title,
      String initialPath, Filter... filters) {
    BetterFileDialog dlg = new BetterFileDialog(MODE_MULTI, parent, title,
        toDir(initialPath), toName(initialPath), filters);
    dlg.doModal();
    int n = dlg.multiResult == null ? 0 : dlg.multiResult.length;
    if (n == 0)
      return null;
    // Quirk: multiResult gives relative file names only, we need
    // to add the directory part.
    String[] paths = dlg.multiResult;
    for (int i = 0; i < n; i++)
      paths[i] = new File(dlg.dirResult, paths[i]).getPath();
    return paths;
  }

  /**
   * Pop up an save-file dialog and return the selected file.
   * @param parent - used for dialog positioning on Windows and Linux. May be
   *    null. On MacOS, the dialog is always centered on the screen.
   * @param title - title for the dialog, or null for system default title.
   * @param initialPath - same as for openFile().
   * @param filters - list of zero or more filters user can choosse from. The
   *    first one is selected by default.
   * @return the user's chosen file, or null if canceled by user. The chosen
   * file is then validated against the filter list and, if it does not match,
   * then a default extension is added based on the filter chosen by the user.
   * There is no check for existing files, and no warning if the chosen file
   * already exists. (BUG: On (some) Linux platforms, the first fitler is used
   * for this case, not the one chosen by the user.)
   */
  public static File saveFile(Component parent, String title,
      File initialPath, Filter... filters) {
    BetterFileDialog dlg = new BetterFileDialog(MODE_SAVE, parent, title,
        toDir(initialPath), toName(initialPath), filters);
    dlg.doModal();
    return toFile(dlg.fileResult);
  }
  public static String saveFile(Component parent, String title,
      String initialPath, Filter... filters) {
    BetterFileDialog dlg = new BetterFileDialog(MODE_SAVE, parent, title,
        toDir(initialPath), toName(initialPath), filters);
    dlg.doModal();
    return dlg.fileResult;
  }

  /**
   * Pop up a select-directory dialog and return the selected directory.
   * @param parent - used for dialog positioning on Windows and Linux. May be
   *    null. On MacOS, the dialog is always centered on the screen.
   * @param title - title for the dialog, or null for system default title.
   * @param initialDir - set initial directory for Windows and Linux. On MacOS,
   *    a system "last accessed" default is used. May be null.
   * @return the user's chosen directory, or null if canceled by user.
   */
  public static File pickDir(Component parent, String title, File initialDir) {
    BetterFileDialog dlg = new BetterFileDialog(MODE_DIR, parent, title,
        toDir(initialDir), null, null);
    dlg.doModal();
    return toFile(dlg.dirResult);
  }
  public static String pickDir(Component parent, String title, String initialDir) {
    BetterFileDialog dlg = new BetterFileDialog(MODE_DIR, parent, title,
        toDir(initialDir), null, null);
    dlg.doModal();
    return dlg.dirResult;
  }

  protected String dirResult;
  protected String fileResult;
  protected String[] multiResult; // for multi

  protected static final int MODE_OPEN = 1;
  protected static final int MODE_SAVE = 2;
  protected static final int MODE_MULTI = 3;
  protected static final int MODE_DIR = 4;

  protected int mode;
  protected String title;
  protected String initialDir;
  protected String suggestedFileName;
  protected Filter[] filters;

  protected Component awtParent;
  protected JDialog awtBlocker;
  protected Shell swtShell;
  protected CountDownLatch awtBlockerIsVisible;

  protected BetterFileDialog(int mode, Component parent, String title,
      String initialDir, String suggestedFileName, Filter[] filters) {
    this.mode = mode;
    this.awtParent = parent;
    this.title = title;
    this.initialDir = initialDir;
    this.suggestedFileName = suggestedFileName;
    this.filters = filters;
    this.awtBlockerIsVisible = new CountDownLatch(1);
  }

  // This can be called from AWT/Swing thread or some unrelated thread, but it
  // wasn't designed to be called from the SWT thread.
  protected void doModal() {
    // Determine position
    final Point pt;
    if (awtParent != null) {
      pt = awtParent.getLocationOnScreen();
      Dimension d = awtParent.getSize();
      pt.x += d.width/2;
      pt.y += d.height/2;
      if (traceLevel > 2)
        System.out.println("BetterFileDialog: Centering over parent at " + pt);
    } else {
      pt = null;
    }

    // SWT half
    swtDisplay.asyncExec(() -> openSWTModal(pt));

    // AWT half
    if (EventQueue.isDispatchThread()) {
      openAWTBlocker();
    } else {
      try {
        EventQueue.invokeAndWait(() -> openAWTBlocker());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  // This must be called from SWT thread.
  protected void openSWTModal(Point pt) {
    try {
      // On linux, the FileDialog modal behavior does not work properly: it
      // stays over swtShell, but neither the FileDialog or swtShell stays above
      // other windows (like our awtBlocker window). The best, but still
      // incomplete and non-ideal approach is to style the shell as ON_TOP of
      // all other windows (including other applications, and mark the
      // awtBlocker as non-focusable. Other ideas that don't seem to reliably
      // work:
      // * Use SWT_AWT.new_shell(awtParent), or any variations of this with modal
      //   or ON-TOP styles, so that the FileDialog modal will respect the
      //   AWT parent.
      // * Varous combinations of making awtBlocker non-focusable.
      // * Not using awtBlocker at all.
      // * Listening for window, focus, component, or other AWT events on
      //   awtBlocker and/or awtParent, then trying to bring the shell or
      //   FileDialog back into focus or back to the top of the window stack.
      // * Calling shell.forceActive() or shell.setActive() whenever awtBlocker
      //   gains focus. The problem here is that display.asyncExec() seems to
      //   sometimes miss events due to some kind of race condition, and
      //   only executes them much later after other events, or sometimes
      //   seeming not executing them at all.
      // * Using shell.syncExecute() instead. This one has the same race
      //   condition apparently, but also hangs because the event doesn't complete
      //   in a timely fashion (or at all). One suspect for the race is the
      //   event loop, which typically looks like:
      //     while (!swtDisplay.isDisposed())
      //       if (!swtDisplay.readAndDispatch())
      //         swtDisplay.sleep();
      //   Perhaps readAndDispatch() returns false because the event queue is
      //   empty, then the asyncEvent/syncEvent queues an event before the
      //   sleep() call? The documentation of sleep() seems to imply that only
      //   *new* events will wake it up, but not existing events. If so, that seems
      //   like a race condition waiting to happen. Unforunately, the event loop
      //   in question is inside FileDialog, not the one we use in the
      //   initializer above. Also, this seems too ovious to be a real bug.
      // * Calling display.wake(), repeatedly in a timed loop with a semaphore
      //   to notify when successful, to try to resolve the race condition.
      // * Calling display.post() via display.syncEvent() repeatedly in a timed
      //   loop with a semaphore to notify when successful, to try to resolve
      //   the race condition.
      // * Using display.post() to send MouseDown events to swtShell whenever
      //   FileDialog loses its focus. The trouble here is reliably knowing when
      //   to send these events. Out of the many listeners attempted on the
      //   awtParent or awtBlocker, none catch even fairly common obvious cases,
      //   like clicking the title bar of awtParent.
      // * Adding an AWT Toolkit listener for AWTEvents doesn't even catch th
      //   title-bar click. It seems that raising and lowering the windows is
      //   entirely done through the system window manager without AWT/Swing/SWT
      //   intervention or notification.
      
      if (isLinux || isWindows)
        swtShell = new Shell(swtDisplay, SWT.ON_TOP);
      else
        swtShell = new Shell(swtDisplay);

      if (pt == null) {
        Rectangle b = swtDisplay.getPrimaryMonitor().getBounds();
        pt = new Point(b.x + b.width/2, b.y + b.height/2);
        if (traceLevel > 2)
          System.out.println("BetterFileDialog: Centering on screen at " + pt);
      }

      if (isWindows) {
        // On Windows, the FileDialog positions itself so the top left corner is
        // on the shell, so we move the shell a bit to the left and up.
        pt.x -= 250;
        pt.y -= 150;
      }

      if (traceLevel > 1) {
        Label x = new Label(swtShell, SWT.BORDER);
        x.setSize(300,30);
        x.setLocation(50, 50);
        x.setText("SWT Shell for BetterFileDialog");
        if (traceLevel < 3)
          swtShell.setAlpha(150);
        swtShell.setSize(400, 100);
        swtShell.setLocation(pt.x - 200, pt.y - 50);
      } else {
        if (isLinux)
          swtShell.setSize(2, 2); // On linux, Gtk prints console messages for size < 2
        else
          swtShell.setSize(0, 0);
        swtShell.setLocation(pt.x, pt.y);
        // swtShell.setVisible(false); // no effect on Linux
        // swtShell.setAlpha(0); // works on Linux
      }

      swtShell.open(); // this is necessary for modal behavior of SWT to work properly

      if (mode == MODE_OPEN) pickFile(SWT.OPEN);
      else if (mode == MODE_MULTI) pickFile(SWT.MULTI);
      else if (mode == MODE_SAVE) pickFile(SWT.SAVE);
      else if (mode == MODE_DIR) pickDirectory();

    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      if (swtShell != null) {
        swtShell.close();
        swtShell.dispose();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Ensure awtBlocker is visible...
    try { awtBlockerIsVisible.await(); }
    catch (Exception e) { e.printStackTrace(); }

    // ... then hide it to notify AWT/Swing thread that result is ready.
    EventQueue.invokeLater(() -> {
      awtBlocker.setVisible(false);
      if (isWindows) {
        // On Windows, when the popup is closed, focus is entirely lost from
        // the application, and some other app is brought to the front. If we
        // have a parent, try to bring it to the front.
        if (awtParent != null)
          awtParent.requestFocus();
      }
    });
  }

  // This must be called from SWT thread.
  protected void pickDirectory() {
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

    dirResult = dialog.open();

    if (traceLevel > 0) {
      System.out.println("BetterFileDialog: Result=" + dirResult);
      System.out.println("BetterFileDialog: FilterPath=" + dialog.getFilterPath());
    }
  }

  // This must be called from SWT thread.
  protected void pickFile(int style) {
    FileDialog dialog = new FileDialog(swtShell, style);
    if (title != null)
      dialog.setText(title);
    if (initialDir != null)
      dialog.setFilterPath(initialDir);

    if (filters != null && filters.length > 0) {
      String[] e = new String[filters.length];
      String[] n = new String[filters.length];
      for (int i = 0; i < filters.length; i ++) {
        e[i] = filters[i].getExtensions();
        n[i] = filters[i].getDescription(); // would name be better for UI?
      }
      dialog.setFilterExtensions(e);
      dialog.setFilterNames(n);
      dialog.setFilterIndex(0);
    } else if (isWindows) {
      // Windows fix: with no filters, Windows adds a poorly-formatted filter
      // named "*.*" with no description. Use the ANY_FILTER instead in this
      // case.
      dialog.setFilterExtensions(new String[] { ANY_FILTER.getExtensions() });
      dialog.setFilterNames(new String[] { ANY_FILTER.getDescription() });
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

    // We set SWT's overwrite-checking to false, so that we can add the default
    // extension, if needed, before doing any such checking.
    dialog.setOverwrite(false);

    String ret = dialog.open();

    if (traceLevel > 0) {
      System.out.println("BetterFileDialog: Result=" + ret);
      System.out.println("BetterFileDialog: FilterPath=" + dialog.getFilterPath());
      System.out.println("BetterFileDialog: FileName=" + dialog.getFileName());
      String[] a = dialog.getFileNames();
      if (a == null) {
        System.out.println("BetterFileDialog: FileNames=(null)");
      } else if (a.length == 0) {
        System.out.println("BetterFileDialog: FileNames=(empty array)");
      } else {
        for (int i = 0; i < a.length; i++)
        System.out.println("BetterFileDialog: FileNames["+i+"]="+a[i]);
      }
    }
    
    if (ret == null)
      return;

    if ("".equals(ret))
      return; // empty filename seems like a bad idea

    if (mode == MODE_SAVE) {
      // Suppose there are two filters: "*.jpg;*.jpeg" and "*.png"
      // and the user selects the "*.jpg;*.jpeg" filter.
      // If the user enters "foo.jpg", we leave it alone, as it
      // matches the chosen filter.
      // If the user enters "foo.png", we leave it alone, as it
      // still matches an acceptable filter.
      // If the user enters "foo", we change it to "foo.jpg"
      // because that's the default extension for the filter they chose.
      if (!matchesFilter(ret, filters)) {
        // This happens only if there are some filters, and none of the filters
        // have wildcards, so we can be assured there is a default extension for
        // whichever filter was chosen by the user.
        int idx = dialog.getFilterIndex();
        // BUG: On (some) Linux platforms, getFilterIndex() does not work. We
        // use the first filter in that case.
        if (idx < 0 || idx >= filters.length) // is this possible?
          idx = 0;
        String ext = filters[idx].getDefaultExtension();
        ret += "." + ext;
      }
      fileResult = ret;
    } else if (mode == MODE_OPEN) {
      fileResult = ret;
    } else if (mode == MODE_MULTI) {
      dirResult = dialog.getFilterPath();
      multiResult = dialog.getFileNames();
    }

  }

  protected static boolean matchesFilter(String path, Filter[] filters) {
    if (filters == null || filters.length == 0)
      return true;
    for (Filter f : filters)
      if (f.accept(path))
        return true;
    return false;
  }

  // This must run on the AWT/Swing thread. This blocks until 
  protected void openAWTBlocker() {
    awtBlocker = 
        (awtParent instanceof Frame) ? new JDialog((Frame)awtParent) :
        (awtParent instanceof Dialog) ? new JDialog((Dialog)awtParent) :
        (awtParent instanceof Window) ? new JDialog((Window)awtParent) :
        new JDialog();

    awtBlocker.setTitle(
        title != null ? title :
        mode == MODE_OPEN ? "Select File" :
        mode == MODE_MULTI ? "Select Files" :
        mode == MODE_SAVE ? "Save File" :
        mode == MODE_DIR ? "Select Directory" : "Unknown");
    awtBlocker.setModal(true);
    if (traceLevel > 1) {
      awtBlocker.setSize(200, 200);
      awtBlocker.setLocation(100, 100);
    } else {
      awtBlocker.setSize(0, 0);
      awtBlocker.setLocation(0, 0); // out of the way?
      awtBlocker.setUndecorated(true);
    }
    awtBlocker.setFocusableWindowState(false);
    awtBlocker.setFocusable(false);

    // Enqueue a task to notify SWT thread that awtBlocker is now visible.
    // Because we are already on the AWT/Swing thread, this task won't get
    // executed until *after* the setVisible(true) below takes effect.
    EventQueue.invokeLater(() -> awtBlockerIsVisible.countDown());

    // Bring up the awtBlocker, to disable all input to AWT/Swing. After making
    // the awtBlocker visible, this blocks until the SWT thread hides it. But
    // during this time AWT/Swing events will still be processed. In particular,
    // the above task we just enqueued to do countDown() will execute after the
    // awtBlocker becomes visible.
    awtBlocker.setVisible(true);

    // By here, SWT thread must have finished and hid awtBlocker.
    awtBlocker.dispose();
  }

  private static File toFile(String path) {
    return path == null ? null : new File(path);
  }

  private static String toPath(File file) {
    return file == null ? null : file.getPath();
  }

  private static String toDir(File path) {
    if (path == null)
      return null;
    if (path.isDirectory())
      return path.getPath();
    File parent = path.getParentFile();
    if (parent != null)
      return parent.getPath();
    return null;
  }

  private static String toName(File path) {
    if (path == null)
      return null;
    if (!path.isDirectory())
      return path.getName();
    return null;
  }

  private static String toDir(String path) {
    if (path == null)
      return null;
    if (path.endsWith(File.separator))
      return path;
    return toDir(new File(path));
  }

  private static String toName(String path) {
    if (path == null)
      return null;
    if (path.endsWith(File.separator))
      return null;
    return toName(new File(path));
  }

  // Some pre-defined filters, for convenience.
  public static final Filter ANY_FILTER = new Filter("All Files", "*");
  public static final Filter JPG_FILTER = new Filter("JPEG Images", "jpg", "jpeg", "jpe", "jfi", "jfif", "jfi");
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

    private String name;
    private String description;
    private String[] extensions; // at least one entry, all non-null, mixed case
    private String permutations; // semi-colon delineated case list of case-permuted extensions
    private boolean wildcard;
    private String defaultExtension;

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
     * case sensitivity depends on the platform, but includes at least
     * ORigINalCase, lower.case, UPPER.CASE, and Title.Case (where dots separate
     * words). 
     */
    public Filter(String name, String... extension)
    {
      this.name = name;

      wildcard = false;
      if (extension == null || extension.length == 0) {
        extensions = new String[] { "*" };
        wildcard = true;
      } else {
        extensions = new String[extension.length];
        for (int i = 0; i < extension.length; i++) {
          String ext = extension[i];
          if (ext == null)
            ext = "*";
          else if (ext.startsWith("*."))
            ext = ext.substring(2);
          else if (ext.startsWith("."))
            ext = ext.substring(1);
          extensions[i] = ext; // mixed case
          if ("*".equals(ext))
            wildcard = true;
          else if (defaultExtension == null)
            defaultExtension = ext;
        }
      }

      description = name + " (*." + extensions[0];
      for (int i = 1; i < extensions.length; i++)
        description += ", *." + extensions[i]; // mixed case
      description += ")"; 

      if (wildcard) {
        permutations = "*"; // "*.*" doesn't work for files with no extension on Linux
      } else {
        // generate case permutations
        ArrayList<String> p = new ArrayList<>();
        for (String ext : extensions) {
          if (ext.length() > 4) {
            p.add(ext); // mixed case
            String lower = ext.toLowerCase();
            if (!lower.equals(ext))
              p.add(lower);
            String upper = ext.toUpperCase();
            if (!upper.equals(ext))
              p.add(upper);
            String title = titleCase(ext);
            if (!title.equals(ext) && !title.equals(upper) && !title.equals(lower))
              p.add(title);
          } else {
            // permute case
            char[] word = ext.toLowerCase().toCharArray();
            int n = 1 << word.length;
            for (int i = 0; i < n; i++) {
              String e = casePermutation(word, i);
              if (e != null)
                p.add(e);
            }
          }
        }
        permutations = "*." + p.get(0);
        for (int i = 1; i < p.size(); i++)
          permutations += ";*." + p.get(i);
      }
    }

    private static String titleCase(String ext) {
      char[] word = ext.toCharArray();
      int n = word.length;
      boolean sawWordBreak = true;
      for (int i = 0; i < n; i++) {
        char l = Character.toLowerCase(word[i]);
        char u = Character.toUpperCase(word[i]);
        if (l == u) {
          sawWordBreak = true;
        } else if (sawWordBreak) {
          word[i] = u;
          sawWordBreak = false;
        } else {
          word[i] = l;
        }
      }
      return new String(word);
    }

    private static String casePermutation(char[] word, int bits) {
      int n = word.length;
      char[] perm = new char[n];
      for (int i = 0; i < n; i++) {
        char c = word[i];
        if ((bits & (1 << i)) != 0) {
          char u = Character.toUpperCase(c);
          if (u == c)
            return null;
          perm[i] = u;
        } else {
          perm[i] = c;
        }
      }
      return new String(perm);
    }

    // Return the name of this filter, for example, "Image Files".
    public String getName() { return name; }

    // Return a description of this filter, for example,
    // "Image Files (*.png, *.jpg, *.jpeg)".
    @Override
    public String getDescription() { return description; }

    // Return a semi-colon delineated list of extensions, for example,
    // "*.jpg;*.png;*.jpeg;*.JPG;*.Jpg;*.JPg;..." This includes permutatons of
    // upper/lower case for extensions with four characters or less.
    public String getExtensions() { return permutations; }

    // Return whether this filter accepts all filenames.
    public boolean isWildcard() { return wildcard; }

    // Return the default extension, i.e. the first non-wildcard extension in
    // the list of accepted extensions.
    public String getDefaultExtension() { return defaultExtension; }

    /**
     * Check if a given directory/file pair matches one of the allowed
     * extensions.
     * @param dir - The directory in which the file was found.
     * @param name - The name of the file.
     * @return true iff the name matches one fo the allowed extensions.
     * NOTE: This isn't used by SWT, but can be used, for example, to check if a
     * result matches a desired filter.
     */
    public boolean accept(File dir, String name) {
      return name == null || name.length() == 0 || accept(new File(dir, name));
    }

    /**
     * Check if a given file or directory matches one of the allowed
     * extensions.
     * @param path - The file or directory.
     * @return true iff the name matches one fo the allowed extensions.
     * NOTE: This isn't used by SWT, but can be used, for example, to check if a
     * result matches a desired filter. Matching is case-insensitive for all
     * file extensions, regardless of length.
     */
    public boolean accept(File path) {
      if (path == null)
        return false;
      if (path.isDirectory())
        return true;
      String lname = path.getName().toLowerCase();
      for (String ext : extensions) {
        if (ext.equals("*") || lname.endsWith("."+ext.toLowerCase()))
          return true;
      }
      return false;
    }
    public boolean accept(String path) {
      return accept(toFile(path));
    }

  } // end of Filter

}
