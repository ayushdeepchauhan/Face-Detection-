package com.attendance.gui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

import com.attendance.dao.StudentDAO;
import com.attendance.dao.impl.JdbcStudentDAO;
import com.attendance.db.DbConfig;
import com.attendance.model.db.Student;
import com.attendance.model.db.Course;
import com.attendance.dao.FaceEmbeddingDAO;
import com.attendance.dao.CourseDAO;
import com.attendance.dao.AttendanceDAO;
import com.attendance.dao.impl.JdbcFaceEmbeddingDAO;
import com.attendance.dao.impl.JdbcCourseDAO;
import com.attendance.dao.impl.JdbcAttendanceDAO;
import com.attendance.model.db.FaceEmbedding;

import com.attendance.pipeline.DJLFaceProcessingPipeline;
import com.attendance.facedetection.RetinaFaceDetection;
import com.attendance.facealignment.DJLStandardFaceAligner;
import com.attendance.facerecogntion.FaceNetRecognition;
import com.attendance.model.recognition.DJLRecognizedFace;
import com.attendance.exception.ModelException;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Paths;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import javax.swing.SwingWorker;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import com.attendance.model.db.AttendanceRecord;


import javax.sql.DataSource;

/**
 * Swing-based GUI for the AutoAttend application. This implementation replaces the original
 * JavaFX version to avoid JavaFX runtime dependencies and IDE configuration issues.
 */
public class AutoAttendSwingGUI extends JFrame {

    private static final long serialVersionUID = 1L;

    private StudentDAO studentDAO;
    private FaceEmbeddingDAO embeddingDAO;
    private CourseDAO courseDAO;
    private AttendanceDAO attendanceDAO;
    private DJLFaceProcessingPipeline facePipeline;
    private JTable studentTable;
    private JTable courseTable;
    private JLabel videoLabel;
    private JTable attendanceTable;
    private JTable historyTable;
    private JComboBox<com.attendance.model.db.Course> courseSelector;
    private SwingWorker<Void, ImageIcon> liveWorker;
    private volatile boolean liveRunning = false;
    private final Java2DFrameConverter frameConverter = new Java2DFrameConverter();
    private Set<Long> sessionMarked = new HashSet<>();
    private Map<Long, float[]> embeddingCache = new HashMap<>();
    

    public AutoAttendSwingGUI() {
        setTitle("AutoAttend - Automatic Attendance System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null); // center on screen

        // Initialise DAO (database connection, etc.)
        try {
            DataSource dataSource = DbConfig.getDataSource();
            studentDAO = new JdbcStudentDAO(dataSource);
            embeddingDAO = new JdbcFaceEmbeddingDAO(dataSource);
            courseDAO = new JdbcCourseDAO(dataSource);
            attendanceDAO = new JdbcAttendanceDAO(dataSource);
            try {
                facePipeline = new DJLFaceProcessingPipeline(
                        new RetinaFaceDetection(),
                        new DJLStandardFaceAligner(),
                        new FaceNetRecognition());
            } catch (ModelException me) {
                me.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Failed to initialize face models: " + me.getMessage(),
                        "Model Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to initialise database connection: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }

        initUI();
        loadStudents();
        loadCourses();
    }

    /**
     * Build the main window layout.
     */
    // Helper to create placeholder panels for tabs
    private JPanel createPlaceholderTab(String message) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(message, SwingConstants.CENTER), BorderLayout.CENTER);
        return panel;
    }

    private void initUI() {
        JTabbedPane tabbedPane = new JTabbedPane();

        tabbedPane.addTab("Student Enrollment", createStudentEnrollmentTab());
        tabbedPane.addTab("Course Management", createCourseManagementTab());
        tabbedPane.addTab("Live Attendance", createLiveAttendanceTab());
        tabbedPane.addTab("Attendance History", createHistoryTab());

        add(tabbedPane, BorderLayout.CENTER);
    }

    /**
     * Creates the Student Enrollment tab (table + buttons).
     */
    private JPanel createStudentEnrollmentTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel("Manage Students");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));

        // Table and model
        studentTable = new JTable();
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"ID", "Student ID", "First Name", "Last Name"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only
            }
        };
        studentTable.setModel(model);
        JScrollPane scrollPane = new JScrollPane(studentTable);

        // Buttons
        JButton addButton = new JButton("Add Student");
        JButton removeButton = new JButton("Remove Student");
        JButton viewCoursesButton = new JButton("Show Courses");
        JButton refreshButton = new JButton("Refresh List");

        viewCoursesButton.addActionListener(e -> showCoursesOfSelectedStudent());
        refreshButton.addActionListener(e -> loadStudents());
        addButton.addActionListener(e -> showAddStudentDialog());
        removeButton.addActionListener(e -> removeSelectedStudent());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(viewCoursesButton);
        buttonPanel.add(refreshButton);

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Queries the database and populates the student table.
     */
    /* obsolete placeholder methods removed
        JTextField studentIdField = new JTextField();
        JTextField firstNameField = new JTextField();
        JTextField lastNameField = new JTextField();

        JPanel form = new JPanel(new GridLayout(0, 2, 5, 5));
        form.add(new JLabel("Student ID:"));
        form.add(studentIdField);
        form.add(new JLabel("First Name:"));
        form.add(firstNameField);
        form.add(new JLabel("Last Name:"));
        form.add(lastNameField);

        int result = JOptionPane.showConfirmDialog(this, form, "Add Student", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String sid = studentIdField.getText().trim();
            String first = firstNameField.getText().trim();
            String last = lastNameField.getText().trim();
            if (sid.isEmpty() || first.isEmpty() || last.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields are required.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                Student s = new Student(sid, first, last);
                studentDAO.save(s);
                loadStudents();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Failed to add student: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Handler to remove the selected student
    // removed placeholder method
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a student to remove.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Long id = (Long) studentTable.getModel().getValueAt(selectedRow, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to remove student ID " + id + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                studentDAO.delete(id);
                loadStudents();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Failed to remove student: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

*/
    private void loadStudents() {
        if(studentDAO==null) return;
        try {
            List<Student> students = studentDAO.findAll();
            DefaultTableModel model = (DefaultTableModel) studentTable.getModel();
            model.setRowCount(0);
            for(Student s: students){
                model.addRow(new Object[]{s.getId(), s.getStudentId(), s.getFirstName(), s.getLastName()});
            }
        }catch(Exception e){
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to load students: "+e.getMessage());
        }
    }

    // ---------------- View helpers ----------------
    private void showCoursesOfSelectedStudent(){
        int row = studentTable.getSelectedRow();
        if(row == -1){ JOptionPane.showMessageDialog(this,"Select a student first"); return; }
        Long id = (Long) studentTable.getModel().getValueAt(row,0);
        try{
            List<Course> courses = courseDAO.findCoursesByStudentId(id);
            if(courses.isEmpty()){
                JOptionPane.showMessageDialog(this,"Student is not enrolled in any course.");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for(Course c: courses){ sb.append(c.getCourseCode()).append(" - ").append(c.getCourseName()).append("\n"); }
            JTextArea area = new JTextArea(sb.toString()); area.setEditable(false);
            area.setRows(Math.min(courses.size()+1, 15));
            JScrollPane sp = new JScrollPane(area);
            sp.setPreferredSize(new Dimension(400, Math.min(300, (courses.size()+1)*20)));
            JOptionPane.showMessageDialog(this, sp, "Enrolled Courses", JOptionPane.INFORMATION_MESSAGE);
        }catch(Exception ex){ ex.printStackTrace(); JOptionPane.showMessageDialog(this,"Failed: "+ex.getMessage()); }
    }

    private void showStudentsForSelectedCourse(){
        int row = courseTable.getSelectedRow();
        if(row == -1){ JOptionPane.showMessageDialog(this,"Select a course first"); return; }
        Integer cid = (Integer) courseTable.getModel().getValueAt(row,0);
        try{
            List<Student> students = courseDAO.findStudentsByCourseId(cid);
            if(students.isEmpty()){
                JOptionPane.showMessageDialog(this,"No students enrolled in this course.");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for(Student s: students){ sb.append(s.getStudentId()).append(" - ").append(s.getFirstName()).append(" ").append(s.getLastName()).append("\n"); }
            JTextArea area = new JTextArea(sb.toString()); area.setEditable(false);
            area.setRows(Math.min(students.size()+1, 15));
            JScrollPane sp = new JScrollPane(area);
            sp.setPreferredSize(new Dimension(400, Math.min(300, (students.size()+1)*20)));
            JOptionPane.showMessageDialog(this, sp, "Enrolled Students", JOptionPane.INFORMATION_MESSAGE);
        }catch(Exception ex){ ex.printStackTrace(); JOptionPane.showMessageDialog(this,"Failed: "+ex.getMessage()); }
    }

    // ---------------- Course Management Tab ----------------
    private void loadStudentsBackup() {
        if (studentDAO == null) {
            return;
        }
        try {
            List<Student> students = studentDAO.findAll();
            DefaultTableModel model = (DefaultTableModel) studentTable.getModel();
            model.setRowCount(0); // Clear existing rows
            for (Student s : students) {
                model.addRow(new Object[]{s.getId(), s.getStudentId(), s.getFirstName(), s.getLastName()});
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to load students: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------- Course Management Tab ----------------
    private JPanel createCourseManagementTab() {
        JPanel panel = new JPanel(new BorderLayout(10,10));
        panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JLabel title = new JLabel("Manage Courses");
        title.setFont(title.getFont().deriveFont(Font.BOLD,18f));

        courseTable = new JTable();
        DefaultTableModel model = new DefaultTableModel(new Object[]{"ID","Code","Name","Description"},0) {
            public boolean isCellEditable(int r,int c){return false;}
        };
        courseTable.setModel(model);
        JScrollPane scroll = new JScrollPane(courseTable);

        JButton addBtn = new JButton("Add Course");
        JButton removeBtn = new JButton("Remove Course");
        JButton enrollBtn = new JButton("Enroll Student");
        JButton unenrollBtn = new JButton("Unenroll Student");
        JButton viewStudentsBtn = new JButton("Show Students");
        JButton refreshBtn = new JButton("Refresh");

        refreshBtn.addActionListener(e->loadCourses());
        addBtn.addActionListener(e->showAddCourseDialog());
        removeBtn.addActionListener(e->removeSelectedCourse());
        enrollBtn.addActionListener(e->showEnrollStudentDialog(false));
        unenrollBtn.addActionListener(e->showEnrollStudentDialog(true));
        viewStudentsBtn.addActionListener(e->showStudentsForSelectedCourse());

        // reattach new dialog method name


        JPanel btnPanel = new JPanel();
        btnPanel.add(addBtn);btnPanel.add(removeBtn);
        btnPanel.add(enrollBtn);btnPanel.add(unenrollBtn);btnPanel.add(viewStudentsBtn);btnPanel.add(refreshBtn);

        panel.add(title,BorderLayout.NORTH);
        panel.add(scroll,BorderLayout.CENTER);
        panel.add(btnPanel,BorderLayout.SOUTH);

        return panel;
    }

    private void loadCourses(){
        DefaultTableModel m=(DefaultTableModel)courseTable.getModel();
        m.setRowCount(0);
        try {
            List<com.attendance.model.db.Course> courses = courseDAO.findAll();
            for(var c:courses){
                m.addRow(new Object[]{c.getCourseId(),c.getCourseCode(),c.getCourseName(),c.getDescription()});
            }
        }catch(Exception ex){
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,"Failed to load courses: "+ex.getMessage());
        }
    }

    /**
     * Dialog for enrolling or unenrolling a student in the currently selected course.
     * When unenroll==false: list only students NOT yet enrolled.
     * When unenroll==true : list only students currently enrolled.
     */
    private void showEnrollStudentDialog(boolean unenroll){
        int sel = courseTable.getSelectedRow();
        if(sel==-1){ JOptionPane.showMessageDialog(this,"Select a course first"); return; }
        Integer courseId = (Integer) courseTable.getModel().getValueAt(sel,0);
        Course course = courseDAO.findById(courseId);
        if(course==null){ JOptionPane.showMessageDialog(this,"Course not found"); return; }

        List<Student> enrolled = courseDAO.findStudentsByCourseId(courseId);
        java.util.Set<Long> enrolledIds = new java.util.HashSet<>();
        for(Student st: enrolled) enrolledIds.add(st.getId());

        List<Student> candidates = new ArrayList<>();
        if(unenroll){
            candidates.addAll(enrolled);
        }else{
            for(Student st: studentDAO.findAll()) if(!enrolledIds.contains(st.getId())) candidates.add(st);
        }

        if(candidates.isEmpty()){
            JOptionPane.showMessageDialog(this, unenroll?"No students to unenroll":"All students already enrolled.");
            return;
        }

        JComboBox<String> combo = new JComboBox<>();
        java.util.Map<String,Long> map = new java.util.HashMap<>();
        for(Student st: candidates){
            String label = st.getStudentId()+" - "+st.getFirstName()+" "+st.getLastName();
            combo.addItem(label);
            map.put(label, st.getId());
        }
        String title = unenroll?"Unenroll Student":"Enroll Student";
        int res = JOptionPane.showConfirmDialog(this, combo, title+" ("+course.getCourseCode()+")", JOptionPane.OK_CANCEL_OPTION);
        if(res!=JOptionPane.OK_OPTION) return;
        Long sid = map.get(combo.getSelectedItem());
        try{
            if(unenroll){ courseDAO.unenrollStudent(sid, courseId);} else { courseDAO.enrollStudent(sid, courseId); }
            JOptionPane.showMessageDialog(this, (unenroll?"Unenrolled":"Enrolled") + " successfully.");
        }catch(Exception ex){ ex.printStackTrace(); JOptionPane.showMessageDialog(this,"Operation failed: "+ex.getMessage()); }
    }

    private void showAddCourseDialog(){
        JPanel p=new JPanel(new GridLayout(0,2,5,5));
        JTextField code=new JTextField();
        JTextField name=new JTextField();
        JTextField desc=new JTextField();
        p.add(new JLabel("Course Code:"));p.add(code);
        p.add(new JLabel("Course Name:"));p.add(name);
        p.add(new JLabel("Description:"));p.add(desc);
        if(JOptionPane.showConfirmDialog(this,p,"Add Course",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return;
        try{
            String cc=code.getText().trim();String cn=name.getText().trim();String d=desc.getText().trim();
            if(cc.isEmpty()||cn.isEmpty()){JOptionPane.showMessageDialog(this,"Code and Name required");return;}
            if(courseDAO.findByCourseCode(cc)!=null){JOptionPane.showMessageDialog(this,"Course code exists");return;}
            com.attendance.model.db.Course course=new com.attendance.model.db.Course(cc,cn,d);
            courseDAO.save(course);
            loadCourses();
        }catch(Exception ex){
            ex.printStackTrace();JOptionPane.showMessageDialog(this,"Add failed: "+ex.getMessage());
        }
    }

    private void removeSelectedCourse(){
        int row=courseTable.getSelectedRow();
        if(row==-1){JOptionPane.showMessageDialog(this,"Select course to remove");return;}
        Integer cid=(Integer)courseTable.getModel().getValueAt(row,0);
        String ccode=(String)courseTable.getModel().getValueAt(row,1);
        if(JOptionPane.showConfirmDialog(this,"Remove course "+ccode+"?","Confirm",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return;
        try{
            courseDAO.delete(cid);
            loadCourses();
        }catch(Exception ex){ex.printStackTrace();JOptionPane.showMessageDialog(this,"Remove failed: "+ex.getMessage());}
    }



// ---------------- Attendance History Tab ----------------
    private JPanel createHistoryTab(){
        JPanel panel = new JPanel(new BorderLayout(10,10));
        panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        historyTable = new JTable(new DefaultTableModel(new Object[]{"Date","Time","Course","Student ID","Name","Status"},0));
        JScrollPane scroll = new JScrollPane(historyTable);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e->loadHistory());
        panel.add(scroll,BorderLayout.CENTER);
        panel.add(refreshBtn,BorderLayout.SOUTH);
        loadHistory();
        return panel;
    }

    private void loadHistory(){
        DefaultTableModel m=(DefaultTableModel)historyTable.getModel();
        m.setRowCount(0);
        try{
            for(AttendanceRecord rec: attendanceDAO.findAll()){
                Student st = studentDAO.findById(rec.getStudentId());
                String name = st!=null? st.getFirstName()+" "+st.getLastName():"?";
                String courseCode = rec.getCourseId()!=null? courseDAO.findById(rec.getCourseId()).getCourseCode():"-";
                m.addRow(new Object[]{rec.getClassDate(), rec.getEntryTime(), courseCode, st!=null?st.getStudentId():"?", name, rec.getAttendanceStatus()});
            }
        }catch(Exception ex){ex.printStackTrace();JOptionPane.showMessageDialog(this,"Failed to load history: "+ex.getMessage());}
    }

// ---------------- Live Attendance Tab ----------------
    private JPanel createLiveAttendanceTab() {
        JPanel root = new JPanel(new BorderLayout(10,10));
        root.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // Top controls
        JPanel control = new JPanel();
        courseSelector = new JComboBox<>();
        refreshCourseSelector();
        JButton startBtn = new JButton("Start Session");
        JButton stopBtn  = new JButton("Stop");
        stopBtn.setEnabled(false);
        control.add(new JLabel("Course:"));
        control.add(courseSelector);
        control.add(startBtn);
        control.add(stopBtn);

        videoLabel = new JLabel();
        videoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        videoLabel.setPreferredSize(new Dimension(640,480));

        attendanceTable = new JTable(new DefaultTableModel(new Object[]{"Student ID","Name","Time"},0));
        JScrollPane scroll = new JScrollPane(attendanceTable);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, videoLabel, scroll);
        split.setResizeWeight(0.7);

        root.add(control,BorderLayout.NORTH);
        root.add(split,BorderLayout.CENTER);

        // Button handlers
        startBtn.addActionListener(e->{
            com.attendance.model.db.Course course = (com.attendance.model.db.Course) courseSelector.getSelectedItem();
            if(course==null){JOptionPane.showMessageDialog(this,"Select course first");return;}
            startLiveSession(course);
            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);
        });
        stopBtn.addActionListener(e->{
            stopLiveSession();
            startBtn.setEnabled(true);
            stopBtn.setEnabled(false);
        });

        return root;
    }

    private void refreshCourseSelector(){
        courseSelector.removeAllItems();
        try{ for(var c: courseDAO.findAll()) courseSelector.addItem(c);}catch(Exception ex){ex.printStackTrace();}
    }

    private void startLiveSession(com.attendance.model.db.Course course){
        if(liveRunning) return;
        sessionMarked.clear();
        ((DefaultTableModel)attendanceTable.getModel()).setRowCount(0);
        // cache embeddings
        embeddingCache.clear();
        try{ for(var fe: embeddingDAO.findAll()) embeddingCache.put(fe.getStudentId(), toFloatArray(fe.getEmbeddingData())); }catch(Exception ex){ex.printStackTrace();}
        liveWorker = new SwingWorker<>(){
            @Override protected Void doInBackground(){
                liveRunning=true;
                try(OpenCVFrameGrabber grab = new OpenCVFrameGrabber(0)){
                    grab.setImageWidth(640);grab.setImageHeight(480);grab.start();
                    while(liveRunning){
                        Frame frame = grab.grab();
                        if(frame==null) continue;
                        BufferedImage imgBuf = frameConverter.getBufferedImage(frame);
                        if(imgBuf!=null){
                            Image djlImg = ImageFactory.getInstance().fromImage(imgBuf);
                            java.util.List<com.attendance.model.recognition.DJLRecognizedFace> faces = facePipeline.processImage(djlImg);
                            // Draw overlays
                            Graphics2D g2 = imgBuf.createGraphics();
                            g2.setStroke(new BasicStroke(2f));
                            g2.setColor(Color.GREEN);
                            for(var rf:faces){
                                // bounding box
                                var bbox = rf.getBoundingBox().getBounds();
                                int x = (int)(bbox.getX()*imgBuf.getWidth());
                                int y = (int)(bbox.getY()*imgBuf.getHeight());
                                int w = (int)(bbox.getWidth()*imgBuf.getWidth());
                                int h = (int)(bbox.getHeight()*imgBuf.getHeight());
                                g2.drawRect(x,y,w,h);
                                // you can optionally draw landmark dots here if your DJL Landmark implementation
                                // provides key points. Currently commented out for compatibility.
                                float[] emb = rf.getEmbedding();
                                Long matchId = findBestMatch(emb);
                                if(matchId!=null && !sessionMarked.contains(matchId)){
                                    sessionMarked.add(matchId);
                                    Student st = studentDAO.findById(matchId);
                                    ((DefaultTableModel)attendanceTable.getModel()).addRow(new Object[]{st.getStudentId(), st.getFirstName()+" "+st.getLastName(), LocalTime.now()});
                                    AttendanceRecord rec = new AttendanceRecord(matchId, course.getCourseId(), LocalDate.now(), LocalTime.now(), "PRESENT");
                                    attendanceDAO.save(rec);
                                }
                            }
                            g2.dispose();
                            publish(new ImageIcon(imgBuf));
                        }
                    }
                    grab.stop();
                }catch(Exception ex){ex.printStackTrace();}
                return null;
            }
            @Override protected void process(java.util.List<ImageIcon> chunks){
                if(!chunks.isEmpty()) videoLabel.setIcon(chunks.get(chunks.size()-1));
            }
        };
        liveWorker.execute();
    }

    private void stopLiveSession(){
        liveRunning=false;
        if(liveWorker!=null) liveWorker.cancel(true);
    }

    private float[] toFloatArray(byte[] bytes){
        FloatBuffer fb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer();
        float[] arr = new float[fb.capacity()];
        fb.get(arr);
        return arr;
    }

    private Long findBestMatch(float[] emb){
        double best=0.0;Long bestId=null;
        for(var entry: embeddingCache.entrySet()){
            double sim = facePipeline.compareFaces(emb, entry.getValue());
            if(sim>0.5 && sim>best){best=sim;bestId=entry.getKey();}
        }
        return bestId;
    }

// ---------------- Course Management Tab ----------------
    private void showAddStudentDialog() {
        JPanel panel = new JPanel(new GridLayout(0,2,5,5));
        JTextField studentIdField = new JTextField();
        JTextField firstNameField = new JTextField();
        JTextField lastNameField  = new JTextField();
        JLabel imgLabel = new JLabel("No file chosen");
        JButton chooseImg = new JButton("Choose Image");
        final String[] imagePath = {null};
        chooseImg.addActionListener(ev -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                imagePath[0] = chooser.getSelectedFile().getAbsolutePath();
                imgLabel.setText(chooser.getSelectedFile().getName());
            }
        });

        panel.add(new JLabel("Student ID:")); panel.add(studentIdField);
        panel.add(new JLabel("First Name:")); panel.add(firstNameField);
        panel.add(new JLabel("Last Name:"));  panel.add(lastNameField);
        panel.add(chooseImg);                  panel.add(imgLabel);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Student", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;
        String sid = studentIdField.getText().trim();
        String fname = firstNameField.getText().trim();
        String lname = lastNameField.getText().trim();
        if (sid.isEmpty()||fname.isEmpty()||lname.isEmpty()||imagePath[0]==null) {
            JOptionPane.showMessageDialog(this,"All fields and image are required.","Validation",JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Student student = new Student(sid,fname,lname);
            Long dbId = studentDAO.save(student);
            if (dbId==null) throw new Exception("Failed to save student record");

            Image img = ImageFactory.getInstance().fromFile(Paths.get(imagePath[0]));
            List<DJLRecognizedFace> faces = facePipeline.processImage(img);
            if (faces==null||faces.isEmpty()) throw new Exception("No face detected in image");
            float[] embedding = faces.get(0).getEmbedding();
            byte[] bytes = floatsToBytes(embedding);
            FaceEmbedding fe = new FaceEmbedding(dbId, bytes);
            embeddingDAO.save(fe);
            JOptionPane.showMessageDialog(this,"Student enrolled successfully!","Success",JOptionPane.INFORMATION_MESSAGE);
            loadStudents();
        } catch(Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,"Enrollment failed: "+ex.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeSelectedStudent() {
        int row = studentTable.getSelectedRow();
        if (row==-1) {
            JOptionPane.showMessageDialog(this,"Select a student to remove.");
            return;
        }
        Long dbId = (Long) studentTable.getModel().getValueAt(row,0);
        String name = (String) studentTable.getModel().getValueAt(row,2)+" "+studentTable.getModel().getValueAt(row,3);
        int confirm = JOptionPane.showConfirmDialog(this,"Remove "+name+"?","Confirm",JOptionPane.YES_NO_OPTION);
        if (confirm!=JOptionPane.YES_OPTION) return;
        try {
            embeddingDAO.deleteByStudentId(dbId);
            attendanceDAO.deleteByStudentId(dbId);
            studentDAO.delete(dbId);
            loadStudents();
        } catch(Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,"Deletion failed: "+ex.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
        }
    }

    private byte[] floatsToBytes(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length*4);
        buffer.order(ByteOrder.BIG_ENDIAN);
        FloatBuffer fb = buffer.asFloatBuffer();
        fb.put(floats);
        return buffer.array();
    }

    // ---------------- Entry Point ----------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AutoAttendSwingGUI().setVisible(true));
    }
}
