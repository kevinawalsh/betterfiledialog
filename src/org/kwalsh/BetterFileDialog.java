package org.kwalsh;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JFileChooser;

// New strategy: Multi-process
//
// * Client code (swing/awt) will launch an entirely new java process, which
//   will be pure SWT and open the dialog. The main process (client side) will
//   make some effort to be sort-of modal, disabling windows, etc., on a best
//   effort basis. This will not be perfect. There will be an SWT menubar,
//   temporarily, while the dialog is displayed. These will be harmless or,
//   perhaps do something useful, hopefully.
//
// * Communication between the main process (client side) and child process
//   (dialog handler) will be over stdin/stdout and command-line arguments.
//
// * There is a race condition (at least on Mac OS), where if the SWT FileDialog
//   is opened too quickly, the SWT menubar doesn't show at all. Maybe that's a
//   good thing? It does seem to temprarily break the apple menu (and the entire
//   menubar). Basically acts like an invisible, unresponsive menubar. It's not
//   clear how reliable this race condition is... even a small sleep is enough
//   to cause the SWT menubar to appear.
//
// Current known issues:
//
// * On MacOS, there is an SWT menubar showing in the main, top of screen
//   application bar. It isn't currently functional, and is actively misleading
//   as the Quit, Help, and other options don't work properly. These don't look
//   easy to make work, and it seems impossible to remove or eliminate the
//   menubar. Worse, the presence of the SWT menubar blocks AWT/Swing menubars
//   from being placed in the main, top of screen application bar, so they
//   appear inside AWT/Swing windows (Frame/JFrame, Dialog/JDialog) instead.
//   (This might happen on Linux Unity-based desktops too? Not tested.)
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
//
// * We warn on overwrite for save-file for all platforms, because this can't
//   reliably be disabled on some platforms. On MacOS 10.15 and later, there can
//   be some cases where two somewhat contradictory warnings will occur, one
//   from the system, and one from BetterFileDialog after changing the name to
//   add a proper extension (but usually, MacOS adds a reasonable extension
//   already, so in the common case the user sees only the MacOS warning).

public class BetterFileDialog {

  public static final String version = "2.0.0";

  // For debugging, zero means no printing, higher values yield more output.
  public static int traceLevel = 0;

  // Application name, (unavoidably) shown in the SWT menubar.
  public static String appName = null;

  // Application-specific error handler.
  public static Consumer<String> errorHandler;

  protected static void trace(int lvl, String msg) {
    if (traceLevel >= lvl)
      System.out.println("BetterFileDialog: " + msg);
  }

  protected static boolean isMacOS;
  protected static boolean isLinux;
  protected static boolean isWindows;
  protected static String peerClassPath;
  protected static String javaExePath;

  static {
    isMacOS = System.getProperty("os.name").toLowerCase().startsWith("mac");
    isLinux = System.getProperty("os.name").toLowerCase().startsWith("linux");
    isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
  }

  private static boolean installed = false;
  private static boolean fallback = false;
  private static Object lock = new Object();
  public static String install() {
    synchronized(lock) {
      if (installed)
        return null;
      installed = true;
      try {
        String sep = System.getProperty("path.separator");
        javaExePath = ProcessHandle.current().info().command().orElse("java");

        // Get checksums for jars
        HashMap<String, String> checksums = new HashMap<>();
        String rsrc = "/bfd-swt-peer/sha256.txt";
        try (InputStream is = BetterFileDialog.class.getResourceAsStream(rsrc);
            Reader isr = new InputStreamReader(is);
            BufferedReader r = new BufferedReader(isr)) {
          r.lines().forEachOrdered((line) -> {
            line = line.trim();
            trace(4, "checksum " + line);
            String[] parts = line.split("  ");
            if (parts.length == 2)
              checksums.put(parts[1], parts[0]);
          });
        }
        String bfd_jar = installJar("bfd-peer.jar", checksums);
        String swt_jar;
        if (isMacOS)
          swt_jar = installJar("swt-macos.jar", checksums);
        else if (isWindows)
          swt_jar = installJar("swt-windows.jar", checksums);
        else // linux
          swt_jar = installJar("swt-linux.jar", checksums);
        peerClassPath = swt_jar + sep + bfd_jar;
        return null;
      } catch (Throwable e) {
        fallback = true;
        e.printStackTrace();
        return e.getMessage();
      }
    }
  }

  protected static void ensureDir(File dir) throws Exception {
    if (!dir.exists()) {
      try {
        dir.mkdir();
      } catch (Exception e) {
        e.printStackTrace();
        throw new Exception("Can't create ~/.swt directory: " + e.toString());
      }
      if (!dir.exists())
        throw new Exception("Can't create ~/.swt directory: mkdir failed");
    }
    if (!dir.isDirectory()) {
      throw new Exception("Can't create ~/.swt directory: path exists but is not a directory. Perhaps delete it and try again.");
    }
  }

  protected static String installJar(String jarname, HashMap<String, String> checksums) throws Exception {
    // First, see if jar is already on classpath
    // String[] paths = System.getProperty("java.class.path").split(File.pathSeparator);
    // for (String path: paths) {
    //   if (new File(path).getName().toLowerCase().equals(jarname.toLowerCase())) {
    //     trace(1, "Found platform-specific library: " + path);
    //     return path;
    //   }
    // }

    String checksum = checksums.get("bfd-swt-peer/"+jarname);
    if (checksum == null)
      throw new Exception("Missing checksum for platform-specific library: " + jarname);

    // Second, check ~/.swt/checksum/jarname, extract if not found
    File swtdir = new File(System.getProperty("user.home"), ".swt");
    File destdir = new File(swtdir, checksum);
    File dest = new File(destdir, jarname);
    if (dest.exists()) {
      trace(1, "Loading platform-specific library: " + dest.getPath());
    } else {
      trace(1, "Installing platform-specific library: " + dest.getPath());
      // Create the ~/.swt directory and ~/.swt/checksum/ directory if needed
      // but don't try to create ~ or anything above it.
      ensureDir(swtdir);
      ensureDir(destdir);
      // Extract the library to ~/.swt/checksum/
      String rsrc = "/bfd-swt-peer/" + jarname;
      try (InputStream inputStream = BetterFileDialog.class.getResourceAsStream(rsrc)) {
        if (inputStream == null) {
          throw new Exception("Required platform-specific library missing: " + rsrc);
        }
        Files.copy(inputStream, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (Throwable e) {
        e.printStackTrace();
        throw new Exception("Can't install platform-specific library to " + dest.getPath() + "\n" + e.toString());
      }
    }

    return dest.getPath();
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
        toString(initialPath), filters);
    dlg.exec();
    return toFile(dlg.fileResult);
  }
  // initialPath - May be null. If it contains a separator, then the trailing
  // part (if non-blank) is used as the suggested name, and the rest is used for
  // the initial directory. Otherwise, the entire initialPath is used for the
  // suggested name.
  public static String openFile(Component parent, String title,
      String initialPath, Filter... filters) {
    BetterFileDialog dlg = new BetterFileDialog(MODE_OPEN, parent, title,
        initialPath, filters);
    dlg.exec();
    return dlg.fileResult;
  }

  /**
   * Same as openFile(), but allows selecting multiple files.
   * @return the list of one or more chosen files, or null if canceled by user.
   */
  public static File[] openFiles(Component parent, String title,
      File initialPath, Filter... filters) {
    BetterFileDialog dlg = new BetterFileDialog(MODE_MULTI, parent, title,
        toString(initialPath), filters);
    dlg.exec();
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
        initialPath, filters);
    dlg.exec();
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
   *    file is then validated against the filter list and, if it does not
   *    match, then a default extension is added based on the filter chosen by
   *    the user. (BUG: On (some) Linux platforms, the first fitler is used for
   *    this case, not the one chosen by the user.) If the file exists but is
   *    not writeable or is a directory, an error is shown. If the file exists
   *    and is writable, the user is warned about overwriting the file with a
   *    chance to cancel.
   */
  public static File saveFile(Component parent, String title,
      File initialPath, Filter... filters) {
    BetterFileDialog dlg = new BetterFileDialog(MODE_SAVE, parent, title,
        toString(initialPath), filters);
    dlg.exec();
    return toFile(dlg.fileResult);
  }
  public static String saveFile(Component parent, String title,
      String initialPath, Filter... filters) {
    BetterFileDialog dlg = new BetterFileDialog(MODE_SAVE, parent, title,
        initialPath, filters);
    dlg.exec();
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
        toString(initialDir), null);
    dlg.exec();
    return toFile(dlg.dirResult);
  }
  public static String pickDir(Component parent, String title, String initialDir) {
    BetterFileDialog dlg = new BetterFileDialog(MODE_DIR, parent, title,
        initialDir, null);
    dlg.exec();
    return dlg.dirResult;
  }

  protected String dirResult;
  protected String fileResult;
  protected String[] multiResult; // for multi

  protected static final int MODE_OPEN = 1;
  protected static final int MODE_SAVE = 2;
  protected static final int MODE_MULTI = 3;
  protected static final int MODE_DIR = 4;
  static final String[] PROMPTS = {
    "", "openfile", "savefile", "openfiles", "pickdir"
  };

  protected int mode;
  protected String title;
  protected String initialPath;
  protected Filter[] filters;

  protected Component awtParent;
  protected Point loc;
  protected JDialog awtBlocker;
  protected CountDownLatch awtBlockerIsVisible;

  protected BetterFileDialog(int mode, Component parent,
      String title, String initialPath, Filter[] filters) {
    this.mode = mode;
    this.title = title;
    this.awtParent = parent;
    this.initialPath = initialPath;
    this.filters = filters;
    this.awtBlockerIsVisible = new CountDownLatch(1);

    if (awtParent != null) {
      loc = awtParent.getLocationOnScreen();
      Dimension d = awtParent.getSize();
      loc.x += d.width/2;
      loc.y += d.height/2;
      trace(3, "Centering over parent at " + loc);
    }
  }

  protected void exec() {
    if (fallback) {
      doSwingDialogFallback();
    } else {
      // SWT half
      new Thread(() -> execSWTPeer()).start();

      // AWT half
      if (EventQueue.isDispatchThread()) {
        openAWTBlocker();
      } else {
        try { EventQueue.invokeAndWait(() -> openAWTBlocker()); }
        catch (Exception e) { e.printStackTrace(); }
      }
    }
  }

  protected Process peer;
  protected String peerError;
  protected String peerResultDir;
  protected String[] peerResults = new String[1];
  protected int peerCountLeft = 1;
  protected boolean peerCanceled;
  protected boolean peerCheckedOverwrite = false;
  protected String peerSuggestsExtension;

  // This must be called from background thread.
  protected void execSWTPeer() {
    Thread.currentThread().setName("SWT Peer Thread ");
    try {
      openSWTPeer();
      readSWTPeer();
    } catch (Exception e) {
      peerError = "Exception: " + e.getMessage();
      e.printStackTrace();
    } finally {
      if (peer != null) {
        peer.destroy();
        peer = null;
      }
    }

    // Process results from peer.
    if (peerError != null) {
      trace(1, "Failed due to " + peerError);
    } else if (peerCanceled) {
      // do nothing
      trace(1, "Canceled by user");
    } else if (peerCountLeft != 0) {
      trace(1, "Missing " + peerCountLeft +
            " of " + peerResults.length + " results");
      peerError = "Missing " + peerCountLeft + " of " + peerResults.length + " results from peer.";
    } else if (peerResults.length == 0) {
      trace(1, "Empty results.");
      peerError = "Empty results from peer.";
    } else if (mode == MODE_OPEN || mode == MODE_SAVE) {
      if (peerResults.length != 1) {
        trace(1, "Wanted 1 result but got " + peerResults.length);
        peerError = "Multiple results from peer.";
      } else {
        fileResult = peerResults[0];
      }
    } else if (mode == MODE_MULTI) {
      if (peerResults.length != 1) {
        trace(1, "Wanted 1 result but got " + peerResults.length);
        peerError = "Multiple results from peer.";
      } else if (peerResultDir == null) {
        trace(1, "Missing result directory");
        peerError = "Missing result directory from peer.";
      } else {
        dirResult = peerResultDir;
        multiResult = peerResults;
      }
    } else if (mode == MODE_DIR) {
      if (peerResults.length != 1) {
        trace(1, "Wanted 1 result but got " + peerResults.length);
        peerError = "Multiple results from peer.";
      } else {
        dirResult = peerResults[0];
      }
    }

    // Ensure awtBlocker is visible...
    try { awtBlockerIsVisible.await(); }
    catch (Exception e) { e.printStackTrace(); } // what to do here?

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

  // This must be called from background thread.
  protected void openSWTPeer() throws Exception {

    String err = install();
    if (err != null)
      throw new Exception("installation failed: " + err);
    if (javaExePath == null || peerClassPath == null)
      throw new Exception("BetterFileDialog installation still failed");

    ArrayList<String> cmd = new ArrayList<>();
    cmd.add(javaExePath);
    if (isMacOS)
      cmd.add("-XstartOnFirstThread");
    cmd.add("-cp");
    cmd.add(peerClassPath);
    cmd.add("org.kwalsh.BetterFileDialogPeer");
    if (appName != null) {
      cmd.add("--appname");
      cmd.add(appName);
    }
    cmd.add("--prompt");
    cmd.add(PROMPTS[mode]);
    if (loc != null) {
      cmd.add("--loc");
      cmd.add(loc.x+","+loc.y);
    }
    if (initialPath != null) {
      cmd.add("--path");
      cmd.add(initialPath);
    }
    if (filters != null && filters.length > 0) {
      for (Filter f : filters) {
        cmd.add("--filter");
        cmd.add(f.encodeForPeer());
      }
    }
    if (traceLevel != 0) {
      cmd.add("--debug");
      cmd.add("" + traceLevel);
    }

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true);
    trace(2, "Peer: " + String.join(" ", cmd));

    peer = pb.start();
  }
  
  // This must be called from background thread.
  protected void readSWTPeer() throws Exception {
    try (InputStream is = peer.getInputStream();
        Reader isr = new InputStreamReader(is);
        BufferedReader r = new BufferedReader(isr)) {
      while (true) {
        String line = r.readLine();
        trace(2, "Peer > " + line);
        if (line.startsWith("EXIT")) {
          break;
        } else if (line.startsWith("STATUS: checked overwrite")) {
          peerCheckedOverwrite = true;
        } else if (line.startsWith("STATUS: suggest extension: ")) {
          peerSuggestsExtension = line.substring(27);
          if (peerSuggestsExtension.length() == 0)
            peerSuggestsExtension = null;
        } else if (line.startsWith("STATUS: ")) {
          // ignore...
        } else if (line.startsWith("RESULT: ")) {
          if (peerCountLeft <= 0) {
            peerError = "too many results";
            if (traceLevel <= 1) break;
          } else {
            peerResults[peerResults.length - peerCountLeft] = line.substring(8);
            peerCountLeft--;
            if (traceLevel <= 1 && peerCountLeft == 0) break;
          }
        } else if (line.startsWith("RESULT DIR: ")) {
          peerResultDir = line.substring(12);
        } else if (line.startsWith("RESULT COUNT: ")) {
          try { peerCountLeft = Integer.parseInt(line.substring(14)); }
          catch (Exception e) { }
          if (peerCountLeft <= 0) {
            peerError = "invalid count";
            if (traceLevel <= 1) break;
          } else {
            peerResults = new String[peerCountLeft];
          }
        } else if (line.startsWith("ERROR: ")) {
          peerError = line.substring(7);
          if (traceLevel <= 1) break;
        } else if (line.startsWith("CANCELED")) {
          peerCanceled = true;
          if (traceLevel <= 1) break;
        }
      } 
    }
  }

  // This is designed for simple extensions that do not themselves contain dots.
  // For multi-part extensions containing dots, like "tar.gz", this can
  // sometimes give slightly odd results, suggesting "foo.tar.zip" be replaced
  // with either "foo.tar.tar.gz" or "foo.tar.zip.tar.gz".
  // This should be called on AWT/Swing Thread.
  protected String ensureExtension(String path, String ext) {
    if (path == null)
      return null;
    String dir, name;
    int idx = path.lastIndexOf(File.separator);
    if (idx < 0) {
      dir = "";
      name = path;
    } else {
      dir = path.substring(0, idx+1);
      name = path.substring(idx+1);
    }
    idx = name.lastIndexOf(".");
    if (idx <= 0) {
      // If path is ".../foo", just change it to "foo.ext" without asking.
      // If path is ".../.foo", just change it to ".foo.ext" without asking.
      name += "." + ext;
      return dir + name;
    } else {
      // If path is ".../foo.bar", ask if user prefers "foo.ext" or "foo.bar.ext".
      String suggestA = name.substring(0, idx) + "." + ext;
      String suggestB = name + "." + ext;
      String title = "File Name Extension";
      String msg = "Missing expected file name extension.";
      Object[] options = { "Use " + suggestA, "Use " + suggestB, "Cancel" };
      JOptionPane dlog = new JOptionPane(msg);
      dlog.setMessageType(JOptionPane.QUESTION_MESSAGE);
      dlog.setOptions(options);
      dlog.createDialog(awtParent, title).setVisible(true);
      Object result = dlog.getValue();
      if (result == options[0])
        return dir + suggestA;
      else if (result == options[1])
        return dir + suggestB;
      else
        return null; // cancel
    }
  }

  protected static boolean matchesFilterExtension(String path, ArrayList<Filter> filters) {
    if (filters == null || filters.size() == 0)
      return true;
    for (Filter f : filters)
      if (f.acceptExtension(path))
        return true;
    return false;
  }

  // This must run on the AWT/Swing thread. This blocks until result is ready.
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

    // Clever trick...
    // Enqueue a task to notify background thread that awtBlocker is now visible.
    // Because we are already on the AWT/Swing thread, this task won't get
    // executed until *after* the setVisible(true) below takes effect.
    EventQueue.invokeLater(() -> awtBlockerIsVisible.countDown());

    // Bring up the awtBlocker, to disable all input to AWT/Swing. After making
    // the awtBlocker visible, this blocks until the background thread hides it. But
    // during this time AWT/Swing events will still be processed. In particular,
    // the above task we just enqueued to do countDown() will execute after the
    // awtBlocker becomes visible.
    awtBlocker.setVisible(true);

    // By here, background thread must have finished and hid awtBlocker.
    awtBlocker.dispose();

    if (peerError != null && errorHandler != null) {
      try { errorHandler.accept(peerError); }
      catch (Exception e) { e.printStackTrace(); }
    }

    if (peerError != null) {
      fallback = true;
      doSwingDialogFallback();
      return;
    }

    // Check extension and overwrite after save dialogs
    if (mode == MODE_SAVE && fileResult != null)
      checkOverwrite();
  }
  
  // This must run on the AWT/Swing thread.
  protected void checkOverwrite() {

    // Change extension if needed
    if (peerSuggestsExtension != null) {
      String replacement = ensureExtension(fileResult, peerSuggestsExtension);
      if (replacement == null) { // user canceled
        fileResult = null;
        return;
      }
      if (!replacement.equals(fileResult)) {
        // name changed, re-confirm overwriting
        fileResult = replacement;
        peerCheckedOverwrite = false;
      }
    }

    // Sanity check: can't write to directory
    File file = new File(fileResult);
    if (file.isDirectory()) {
      JOptionPane.showMessageDialog(awtParent,
          "A directory named \"" + file.getName() + "\" already exists.",
          "Error Saving File", JOptionPane.OK_OPTION);
      fileResult = null;
      return;
    }

    // Sanity check: can't write to protected file
    if (file.exists() && !file.canWrite()) {
      JOptionPane.showMessageDialog(awtParent,
          "Permission denied: " + file.getName(),
          "Error Saving File", JOptionPane.OK_OPTION);
      fileResult = null;
      return;
    }

    // Sanity check: warn on overwrite, if SWT hasn't already done so
    if (file.exists() && !peerCheckedOverwrite) {
      int confirm = JOptionPane.showConfirmDialog(awtParent,
          "A file named \"" + file.getName() + "\" exists. Overwrite it?",
          "Confirm Overwrite",
          JOptionPane.YES_NO_OPTION);
      if (confirm != JOptionPane.YES_OPTION) {
        fileResult = null;
        return;
      }
    }

  }
  
  static File toDir(File path) {
    return path.isDirectory() ? path : path.getParentFile();
  }
  
  static File toFilePath(String path) {
    if (path.endsWith(File.separator))
      return null;
    File f = new File(path);
    return f.isDirectory() ? null : f;
  }

  // This must run on the AWT/Swing thread.
  protected void doSwingDialogFallback() {
    peerCheckedOverwrite = false;
    if (mode == MODE_OPEN || mode == MODE_SAVE || mode == MODE_MULTI) {
      JFileChooser fc = new JFileChooser();
      if (this.title != null)
        fc.setDialogTitle(title);
      if (initialPath != null) {
        File path = new File(initialPath);
        File dir = toDir(path);
        File file = toFilePath(initialPath);
        if (dir != null)
          fc.setCurrentDirectory(dir);
        if (file != null)
          fc.setSelectedFile(file);
      }
      if (filters != null && filters.length > 0) {
        for (Filter f : filters)
          fc.addChoosableFileFilter(f);
        fc.setFileFilter(filters[0]);
      }
      if (mode == MODE_MULTI)
        fc.setMultiSelectionEnabled(true);
      int ret;
      if (mode == MODE_SAVE)
        ret = fc.showSaveDialog(awtParent);
      else
        ret = fc.showOpenDialog(awtParent);
      if (ret == JFileChooser.APPROVE_OPTION) {
        if (mode == MODE_MULTI) {
          dirResult = fc.getCurrentDirectory().getPath();
          File[] files = fc.getSelectedFiles();
          multiResult = new String[files.length];
          for (int i = 0; i < files.length; i++)
            multiResult[i] = files[i].getPath();
        } else {
          fileResult = fc.getSelectedFile().getPath();
          if (mode == MODE_SAVE && filters != null && filters.length > 0) {
            Filter usedFilter = filters[0];
            javax.swing.filechooser.FileFilter chosenFilter = fc.getFileFilter();
            if (chosenFilter instanceof Filter)
              usedFilter = (Filter)chosenFilter;
            peerSuggestsExtension = usedFilter.getDefaultExtension();
          } 
        }
      }
    } else if (mode == MODE_DIR) {
      JFileChooser fc = new JFileChooser();
      if (this.title != null)
        fc.setDialogTitle(title);
      if (initialPath != null) {
        File path = new File(initialPath);
        File dir = toDir(path);
        if (dir != null) {
          File parent = dir.getParentFile();
          if (parent != null)
            fc.setCurrentDirectory(parent);
          fc.setSelectedFile(dir);
        }
      }
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      int ret = fc.showOpenDialog(awtParent);
      if (ret == JFileChooser.APPROVE_OPTION) {
        dirResult = fc.getSelectedFile().getPath();
      }
    }

    // Check extension and overwrite after save dialogs
    if (mode == MODE_SAVE && fileResult != null)
      checkOverwrite();
  }

  private static String toString(File path) {
    return path == null ? null : path.getPath();
  }

  private static File toFile(String path) {
    return path == null ? null : new File(path);
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
      if (name == null)
        throw new IllegalArgumentException("Name must not be null");
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
      if (path.isDirectory() || wildcard)
        return true;
      String lname = path.getName().toLowerCase();
      for (String ext : extensions) {
        if (lname.endsWith("."+ext.toLowerCase()))
          return true;
      }
      return false;
    }
    public boolean accept(String path) {
      return accept(toFile(path));
    }

    /**
     * Check if a given file name matches one of the allowed extensions.
     * @param path - The file name or file path.
     * @return true iff the name matches one fo the allowed extensions.
     * NOTE: Anything before a File.separator is ignored, and the file name
     * portion must be more than just a dot followed by the extension. For
     * example, the name "/foo/a.tar.gz" matches "gz" extension because it
     * ends with ".gz". It matches the "tar.gz" extension for the same
     * reason, but "/foo/.tar.gz" does not, because this file name is just a
     * dot followed by the "tar.gz" extension.
     * Matching is case-insensitive for all file extensions, regardless of
     * length.
     */
    public boolean acceptExtension(String path) {
      if (path == null)
        return false;
      if (wildcard)
        return true;
      int idx = path.lastIndexOf(File.separator);
      if (idx >= 0)
        path = path.substring(idx+1);
      String lname = path.toLowerCase();
      if (lname.equals(""))
          return false; // empty name can't possibly match an extension
      String lnameSuffix = lname.substring(1); // don't match first char
      for (String ext : extensions) {
        if (lnameSuffix.endsWith("."+ext.toLowerCase()))
          return true;
      }
      return false;
    }

    protected String encodeForPeer() {
      String s = name + ":" + extensions[0];
      for (int i = 1; i < extensions.length; i++)
        s += "," + extensions[i];
      return s;
    }

  } // end of Filter

}
