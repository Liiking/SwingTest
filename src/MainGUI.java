import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Created by qwy on 17/9/27.
 *
 * 1.java文件转换成class文件：          javac -source 1.6 -target 1.6 X.java（X.java是要转换的文件，默认生成同名class文件）
 * 2.class.dex文件转换成jar文件：       jar cvf X.jar Y.class（Y.class是要转换的文件，X.jar是转换后的文件）    d2j-dex2jar classes.dex
 * 3.将jar文件转换成dex二进制jar文件：   dx --dex --output=target.jar origin.jar
 * 4.指定文件夹打包成apk
 * 5.指定文件夹打包成zip压缩包    zip -q -r -e -m -o [yourName].zip someThing
 */
public class MainGUI implements ChangeListener {
    private JPanel panel1;
    private JRadioButton rbJar2Dex;// 选项 jar转dex
    private JRadioButton rbDex2Jar;// 选项 dex转jar
    private JTextField srcPath;// 源文件目录
    private JTextField desPath;// 目标文件目录
    private JButton btnSelect;// 选择源文件
    private JButton btnOpen;// 打开目标文件目录
    private JButton btnExec;// 执行
    private JTextArea textArea1;// 日志描述
    private JLabel labelSrc;// 源文件
    private JLabel labelDes;// 目标文件
    private JButton btnClear;
    private JScrollPane jPane;
    private JRadioButton rbDex;
    private JPanel panel2;
    private ButtonGroup group;

    private static final int EXEC_TYPE_GET_DEX = 0, EXEC_TYPE_DEX2JAR = 1, EXEC_TYPE_JAR2DEX = 2;// 操作类型  0 - 提取dex   1 - dex2jar   2 - jar2dex

    private String curDir, curOs, curArch;

    public MainGUI() {

        curDir = System.getProperty("user.dir");// 当前路径
        curOs = System.getProperty("os.name");// 当前操作系统
        curArch = System.getProperty("os.arch");// 当前操作系统架构
        printLog("当前目录：" + curDir + ", 当前操作系统：" + curOs + ", 操作系统架构：" + curArch);

        group = new ButtonGroup();// 初始化按钮组
        group.add(rbDex);// 加入按钮组
        group.add(rbDex2Jar);// 加入按钮组
        group.add(rbJar2Dex);// 加入按钮组
        rbDex.setSelected(true);

        textArea1.setLineWrap(true);// 激活自动换行功能
        textArea1.setWrapStyleWord(true);// 激活断行不断字功能
        outputUI();

        // 支持拖拽选取文件或目录
        srcPath.setTransferHandler(new TransferHandler() {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean importData(JComponent comp, Transferable t) {
                try {
                    Object o = t.getTransferData(DataFlavor.javaFileListFlavor);
                    String filepath = o.toString();
                    if (filepath.startsWith("[")) {
                        filepath = filepath.substring(1);
                    }
                    if (filepath.endsWith("]")) {
                        filepath = filepath.substring(0, filepath.length() - 1);
                    }
                    if (!isEmpty(filepath)) {// 这里也可以做文件类型过滤
                        srcPath.setText(filepath);
                        refreshTarget();
                    }
                    return true;
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
            @Override
            public boolean canImport(JComponent comp, DataFlavor[] flavors) {
                for (int i = 0; i < flavors.length; i++) {
                    if (DataFlavor.javaFileListFlavor.equals(flavors[i])) {
                        return true;
                    }
                }
                return false;
            }
        });

        btnExec.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String src = srcPath.getText();
                String target = desPath.getText();
                if (isEmpty(src)) {
                    showMessage("请先选择源文件");
                    return ;
                }
                int type = getExecType();
                switch (type) {
                    case EXEC_TYPE_GET_DEX:
                        if (!src.endsWith(".apk") && !src.endsWith(".jar") && !src.endsWith(".zip")) {
                            printLog("源文件格式不正确，请选择压缩文件(.apk .jar .zip)");
                            return ;
                        }
                        getDex(src);
                        break;
                    case EXEC_TYPE_DEX2JAR:
                        if (!src.endsWith(".dex")) {
                            printLog("源文件格式不正确，请选择dex文件");
                            return ;
                        }
                        dex2jar(src, target);
                        break;
                    case EXEC_TYPE_JAR2DEX:
                        if (!src.endsWith(".jar")) {
                            printLog("源文件格式不正确，请选择jar文件");
                            return ;
                        }
                        jar2dex(src, target);
                        break;
                }
            }
        });

        btnSelect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int type = getExecType();
                if (type == EXEC_TYPE_DEX2JAR) {
                    fileChooser("dex");
                } else if (type == EXEC_TYPE_JAR2DEX) {
                    fileChooser("jar");
                } else if (type == EXEC_TYPE_GET_DEX) {
                    fileChooser("jar", "apk", "zip");
                }
            }
        });

        btnClear.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                textArea1.setText("");
            }
        });

        btnOpen.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 打开目标文件所在目录
                String target = desPath.getText();
                printLog("------ 打开目标文件所在目录 ------");
                exec("open " + getDir(target));
            }
        });

        rbDex.addChangeListener(this);
        rbDex2Jar.addChangeListener(this);
        rbJar2Dex.addChangeListener(this);

    }

    @Override
    public void stateChanged(ChangeEvent e) {
        // 操作类型改变,刷新目标文件地址
        refreshTarget();
    }

    /**
     * 根据路径获取文件所在目录
     * @param path
     * @return
     */
    private String getDir(String path) {
        if (isEmpty(path)) {
            return "";
        }
        if (new File(path).isDirectory()) {
            return path;
        }
        return path.substring(0, path.lastIndexOf("/"));
    }

    /**
     * 刷新目标文件地址
     */
    private void refreshTarget() {
        int type = getExecType();
        switch (type) {
            case EXEC_TYPE_GET_DEX:
                desPath.setText(curDir + "/classes.dex");
                break;
            case EXEC_TYPE_DEX2JAR:
                desPath.setText(curDir + "/dex2jar-d2j.jar");
                break;
            case EXEC_TYPE_JAR2DEX:
                desPath.setText(curDir + "/jar2dex-j2d.jar");
                break;
        }
    }

    /**
     * 判断给定字符串是否为空
     * @param s
     * @return
     */
    private boolean isEmpty(String s) {
        return s == null || "".equals(s);
    }

    private boolean isEmpty(String... s) {
        return s == null || s.length == 0 || isEmpty(s[0]);
    }

    /**
     * 从apk中提取dex到当前目录
     */
    private void getDex(String src) {
        printLog("------ 从apk中提取dex文件 ------");
        String command = "unzip -o " + src + " classes.dex";
        exec(command);
    }

    /**
     * 指定dex文件转成jar包
     */
    private void dex2jar(String src, String target) {
        printLog("------ 将dex文件转成jar包 ------");
        String command = getD2jDir() + " " + src + " --output " + target;
        exec(command);
    }

    /**
     * 指定jar包转成dex二进制格式jar包
     */
    private void jar2dex(String src, String target) {
        printLog("------ 将jar包转成二进制的jar包 ------");
        String command = getDxDir() + " --dex --output=" + target + " " + src;
        exec(command);
    }

    /**
     * 打压缩包
     */
    private void zipFile(String src, String target) {
        printLog("------ 压缩指定文件或目录 ------");
        String command = "zip -q -r -e -m -o " + target + src;
        exec(command);

    }

    private void exec(String command) {
        exec("", command);
    }

    private void exec(String dir, String command) {
        exec(dir, command, true);
    }

    /**
     * 执行命令行
     * @param dir
     * @param command
     * @param show 是否打印命令行 默认打印
     */
    private void exec(String dir, String command, boolean show) {
        if (show) {
            printLog(command);
        }
        Runtime runtime = Runtime.getRuntime();
        try {
            Process process = runtime.exec(dir + command);
            process.waitFor();
            printLog("------ 操作完成 ------ ");
        } catch (IOException e) {
            e.printStackTrace();
            printLog("----- 啊哦，发生错误了~ ----- message : " + e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
            printLog("----- 啊哦，操作中断了~ ----- message : " + e.getMessage());
        }
    }

    /**
     * 显示提示信息框
     * @param message
     */
    private void showMessage(String message) {
        JOptionPane.showMessageDialog(null, message, "提示",  JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * 获取dx命令所在目录
     * @return
     */
    private String getDxDir() {
        return System.getProperty("user.dir") + "/tools/dx";
    }

    /**
     * 获取dex2jar命令所在目录
     * @return
     */
    private String getD2jDir() {
        return System.getProperty("user.dir") + "/tools/dex2jar/d2j-dex2jar.sh";
    }

    /**
     * 获取操作类型
     * @return
     */
    private int getExecType() {
        if (rbJar2Dex.isSelected()) {
            return EXEC_TYPE_JAR2DEX;
        } else if (rbDex2Jar.isSelected()) {
            return EXEC_TYPE_DEX2JAR;
        }
        return EXEC_TYPE_GET_DEX;
    }

    /**
     * 打开文件选择器
     *
     * @param type 要选取的文件类型
     */
    private void fileChooser(String... type) {
        JFileChooser chooser = new JFileChooser(srcPath.getText());
        if (!isEmpty(type)) {
            StringBuilder suffix = new StringBuilder();
            for (String s : type) {
                suffix.append("." + s);
            }
            FileNameExtensionFilter filter = new FileNameExtensionFilter(
                    suffix.toString(), type);
            // 设置文件过滤类型
            chooser.setFileFilter(filter);
        } else {
            // 设置选择文件夹
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        }
        // 打开选择器面板
        int returnVal = chooser.showOpenDialog(new JPanel());
        // 保存文件从这里入手，输出的是文件名
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            srcPath.setText(path);
            refreshTarget();
        }
    }

    /**
     * 捕获控制台输出到GUI界面上
     */
    protected void outputUI(){
        OutputStream textAreaStream = new OutputStream() {
            public void write(int b) throws IOException {
                textArea1.append(String.valueOf((char)b));
            }

            public void write(byte b[]) throws IOException {
                textArea1.append(new String(b));
            }

            public void write(byte b[], int off, int len) throws IOException {
                textArea1.append(new String(b, off, len));
            }
        };
        PrintStream myOut = new PrintStream(textAreaStream);
        System.setOut(myOut);
        System.setErr(myOut);
    }

    /**
     * 打印操作记录
     * @param msg
     */
    private void printLog(String msg) {
        System.out.println(msg);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("MainGUI");
        JPanel rootPane = new MainGUI().panel1;
        frame.setContentPane(rootPane);// 设置要展示的内容
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setSize(550, 550);
        frame.setResizable(false);// 设置不可拉伸
        frame.setLocationRelativeTo(rootPane);// 居中
        frame.setVisible(true);
    }

}
