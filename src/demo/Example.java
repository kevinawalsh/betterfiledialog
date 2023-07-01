// Example.java
// Example and test program for BetterFileDialog.

import org.kwalsh.BetterFileDialog;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import javax.swing.*;

class Example extends JFrame
{

  String[] parents = { "Dialog", "Frame", "Component", "null" };
  String[] types = { "File", "String" };

  private JSpinner iTrace = new JSpinner();
  private JComboBox<String> dParent = new JComboBox<>(parents);
  private JComboBox<String> dType = new JComboBox<>(types);

  private JButton bOpenOne = new JButton("Open File");
  private JButton bOpenMulti = new JButton("Open Files");
  private JButton bSave = new JButton("Save File");
  private JButton bDir = new JButton("Pick Dir");

  private JTextField tTitle = new JTextField("Pick Something");
  private JTextField tDir = new JTextField(new File(System.getProperty("user.home"), "Downloads").getPath());
  private JTextField tFile = new JTextField("example.jpg");
  private JCheckBox cPNG = new JCheckBox();
  private JCheckBox cJPG = new JCheckBox();
  private JCheckBox cTXT = new JCheckBox();
  private JCheckBox cANY = new JCheckBox();
  private JTextArea tResults = new JTextArea(20, 45);

  void addSpace(JPanel main, int w, int h) {
    JLabel lSpace = new JLabel("");
    lSpace.setPreferredSize(new Dimension(w, h));
    main.add(lSpace);
  }

  public Example()
  {

    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setLocation(200,160);
    setPreferredSize(new Dimension(600, 650));
    setTitle("BetterFileDialog Example");

    JPanel main = new JPanel(new BorderLayout());
    main.setLayout(new FlowLayout());

    JLabel lTrace = new JLabel("Tracing level:");
    iTrace.setModel(new SpinnerNumberModel(0, 0, 5, 1));
    iTrace.setValue((Integer)BetterFileDialog.traceLevel);
    iTrace.setPreferredSize(new Dimension(35, 35));

    JLabel lParent = new JLabel("Parent:");
    dParent.setSelectedIndex(1);

    JLabel lType = new JLabel("Type:");
    dType.setSelectedIndex(1);

    JLabel lTitle = new JLabel("Dialog Title:");
    lTitle.setPreferredSize(new Dimension(575, 30));
    tTitle.setPreferredSize(new Dimension(575, 30));

    JLabel lPNG = new JLabel("PNG");
    JLabel lJPG = new JLabel("JPG");
    JLabel lTXT = new JLabel("TXT");
    JLabel lANY = new JLabel("*.*");

    JLabel lDir = new JLabel("Initial Directory:");
    lDir.setPreferredSize(new Dimension(575, 30));
    tDir.setPreferredSize(new Dimension(575, 30));

    JLabel lFile = new JLabel("Initial File:");
    lFile.setPreferredSize(new Dimension(575, 30));
    tFile.setPreferredSize(new Dimension(575, 30));

    bOpenOne.setPreferredSize(new Dimension(125, 36));
    bOpenMulti.setPreferredSize(new Dimension(125, 36));
    bSave.setPreferredSize(new Dimension(125, 36));
    bDir.setPreferredSize(new Dimension(125, 36));

    tResults.setLineWrap(true);

    main.add(lTrace);
    main.add(iTrace);
    addSpace(main, 10, 10);
    main.add(lParent);
    main.add(dParent);
    addSpace(main, 10, 10);
    main.add(lType);
    main.add(dType);
    main.add(lTitle);
    main.add(tTitle);
    main.add(lPNG);
    main.add(cPNG);
    addSpace(main, 40, 10);
    main.add(lJPG);
    main.add(cJPG);
    addSpace(main, 40, 10);
    main.add(lTXT);
    main.add(cTXT);
    addSpace(main, 40, 10);
    main.add(lANY);
    main.add(cANY);
    main.add(lDir);
    main.add(tDir);
    main.add(lFile);
    main.add(tFile);
    main.add(bOpenOne);
    main.add(bOpenMulti);
    main.add(bSave);
    main.add(bDir);
    main.add(tResults);

    getContentPane().add(main);

    iTrace.addChangeListener(e -> setTraceLevel());
    bOpenOne.addActionListener(e -> doDialog("open"));
    bOpenMulti.addActionListener(e -> doDialog("multi"));
    bSave.addActionListener(e ->  doDialog("save"));
    bDir.addActionListener(e ->  doDialog("dir"));

    pack();
  }

  private void setTraceLevel() {
    int i = (Integer)iTrace.getValue();
    BetterFileDialog.traceLevel = i;
  }

  private void doDialog(String mode) {
    if (dParent.getSelectedItem().equals("Frame")) {
      doDialog(mode, this);
    } else if (dParent.getSelectedItem().equals("null")) {
      doDialog(mode, null);
    } else if (dParent.getSelectedItem().equals("Dialog")) {
      Dialog parent = new Dialog(this, "Example Dialog", true);
      parent.setLayout(null);
      Button b = new Button("Click Me");
      b.addActionListener(e -> doDialog(mode, parent));
      b.setBounds(100, 100, 100, 100);
      parent.add(b);
      parent.addWindowListener( new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent we) {
          parent.setVisible(false);
          parent.dispose();
        }
      });
      parent.setSize(300, 300);
      parent.setVisible(true);
    } else if (dParent.getSelectedItem().equals("Component")) {
      Dialog parent = new Dialog(this, "Example Dialog", true);
      parent.setLayout(null);
      parent.setSize(1000, 800);
      Button[] bs = new Button[] {
            new Button("Top Left"),
            new Button("Top Right"),
            new Button("Bottom Left"),
            new Button("Bottom Right"),
            new Button("Center") };
      bs[0].setBounds(20, 40, 100, 100);
      bs[1].setBounds(880, 40, 100, 100);
      bs[2].setBounds(20, 680, 100, 100);
      bs[3].setBounds(880, 680, 100, 100);
      bs[4].setBounds(450, 350, 100, 100);
      for (Button b : bs) {
        b.addActionListener(e -> doDialog(mode, b));
        parent.add(b);
      }
      parent.addWindowListener( new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent we) {
          parent.setVisible(false);
          parent.dispose();
        }
      });
      parent.setVisible(true);
    }
  }

  private void doDialog(String mode, Component parent) {

    tResults.setText("");

    String title = tTitle.getText();
    String initialDir = tDir.getText();
    String initialFile = tFile.getText();
    if (title.equalsIgnoreCase("null"))
      title = null;
    if (initialDir.equalsIgnoreCase("null"))
      initialDir = null;
    if (initialFile.equalsIgnoreCase("null"))
      initialFile = null;

    int n = 0;
    if (cPNG.isSelected()) n++;
    if (cJPG.isSelected()) n++;
    if (cTXT.isSelected()) n++;
    if (cANY.isSelected()) n++;
    BetterFileDialog.Filter[] filters = new BetterFileDialog.Filter[n];
    n = 0;
    if (cPNG.isSelected())
      filters[n++] = BetterFileDialog.PNG_FILTER;
    if (cJPG.isSelected())
      filters[n++] = BetterFileDialog.JPG_FILTER;
    if (cTXT.isSelected())
      filters[n++] = BetterFileDialog.TXT_FILTER;
    if (cANY.isSelected())
      filters[n++] = BetterFileDialog.ANY_FILTER;

    String ret = "";

    ret += "Using " + n + " filters\n";
    for (BetterFileDialog.Filter f : filters) {
      ret += "- accepting " + f.getDescription() + ": " + f.getExtensions() + "\n";
    }
    tResults.setText(ret);

    if (dType.getSelectedItem().equals("File")) {
      File iD = toFile(initialDir);
      File iF = toFile(initialFile);
      if (mode.equals("open")) {
        File f = BetterFileDialog.openFile(parent, title, iD, iF, filters);
        ret += "open: " + f;
      } else if (mode.equals("save")) {
        File f = BetterFileDialog.saveFile(parent, title, iD, iF, filters);
        ret += "save: " + f;
      } else if (mode.equals("multi")) {
        File[] f = BetterFileDialog.openFiles(parent, title, iD, iF, filters);
        if (f == null)
          ret += "open multiple: (null)";
        else if (f.length == 0)
          ret += "open multiple: (empty array ???)"; // should never happen
        else {
          ret += "open multiple: (" + f.length + " items)\n";
          for (File s : f)
            ret += s + "\n";
        }
      } else if (mode.equals("dir")) {
        File f = BetterFileDialog.pickDir(parent, title, iD);
        ret += "dir: " + f + "\n";
      }
    } else { // dType is "String"
      if (mode.equals("open")) {
        String f = BetterFileDialog.openFile(parent, title, initialDir, initialFile, filters);
        ret += "open: " + f;
      } else if (mode.equals("save")) {
        String f = BetterFileDialog.saveFile(parent, title, initialDir, initialFile, filters);
        ret += "save: " + f;
      } else if (mode.equals("multi")) {
        String[] f = BetterFileDialog.openFiles(parent, title, initialDir, initialFile, filters);
        if (f == null)
          ret += "open multiple: (null)";
        else if (f.length == 0)
          ret += "open multiple: (empty array ???)"; // should never happen
        else {
          ret += "open multiple: (" + f.length + " items)\n";
          for (String s : f)
            ret += s + "\n";
        }
      } else if (mode.equals("dir")) {
        String f = BetterFileDialog.pickDir(parent, title, initialDir);
        ret += "dir: " + f + "\n";
      }
    }

    tResults.setText(ret);
  }

  static File toFile(String s) {
    return s == null ? null : new File(s);
  }

  public static void main(String[] args)
  {
    final boolean sysLook = (args.length > 0 && args[0].equals("system"));

    Runnable awtTask = () -> {
      try {
        if (sysLook) {
          System.out.println("Using system look and feel");
          UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
        } else {
          System.out.println("Using default look and feel");
        }
      } catch(Exception e) {
        e.printStackTrace();
      }
      new Example().setVisible(true);
    };

    BetterFileDialog.traceLevel = 0;
    BetterFileDialog.init(awtTask, null, null);
  }

}




