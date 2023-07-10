// ShortExample.java
// A short example program for BetterFileDialog.

import org.kwalsh.BetterFileDialog;
import java.awt.*;
import java.awt.event.*;

class ShortExample {

  static String dir = System.getProperty("user.home");

  public static void main(String[] args) {
    if (args.length > 0)
      dir = args[0];
    Dialog dlg = new Dialog((Frame)null, "Demo", true);
    dlg.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) { dlg.dispose(); }
    });
    dlg.setLayout(null);
    dlg.setSize(300, 300);
    Button b = new Button("Click Me");
    b.setBounds(100, 100, 100, 100);
    b.addActionListener(e -> click(dlg));
    dlg.add(b);
    dlg.setVisible(true);
  }

  public static void click(Dialog parent) {
    String choice = BetterFileDialog.openFile(parent,
        "Pick Your Best Pic", dir,
        new BetterFileDialog.Filter("Tarballs", ".tar", ".tar.gz", ".tgz"),
        BetterFileDialog.JPG_FILTER,
        BetterFileDialog.PNG_FILTER,
        BetterFileDialog.ANY_FILTER);
    if (choice == null)
      System.out.println("Cancelled, goodbye!");
    else
      System.out.println("You picked: " + choice);
  }

}
