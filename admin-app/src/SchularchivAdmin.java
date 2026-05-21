import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SchularchivAdmin {
    private static final String[] CLASS_LEVELS = {"5", "6", "7", "8", "9", "10", "11", "12"};

    private final StateStore stateStore;
    private State state;
    private String adminTeacherFilter = "";
    private String adminSubjectFilter = "";
    private String adminClassFilter = "";
    private String adminTeacherEditorId = "";

    private final DefaultComboBoxModel<Teacher> teacherEditorModel = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<Teacher> approvedTeacherFilterModel = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<String> approvedSubjectFilterModel = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<String> approvedClassFilterModel = new DefaultComboBoxModel<>();

    private final DefaultListModel<UploadEntry> pendingListModel = new DefaultListModel<>();
    private final DefaultListModel<UploadEntry> approvedListModel = new DefaultListModel<>();

    private final JTextField teacherNameField = new JTextField();
    private final JTextField teacherCodeField = new JTextField();
    private final JTextField teacherSubjectsField = new JTextField();
    private final JComboBox<Teacher> teacherEditorCombo = new JComboBox<>(teacherEditorModel);

    private final JList<UploadEntry> pendingList = new JList<>(pendingListModel);
    private final JTextField pendingTitleField = new JTextField();
    private final JTextField pendingYearField = new JTextField();
    private final JTextField pendingClassField = new JTextField();
    private final JTextField pendingSubjectField = new JTextField();
    private final JComboBox<String> pendingTypeCombo = new JComboBox<>(new String[]{"Klassenarbeit", "Test"});
    private final JTextArea pendingNoteArea = new JTextArea(4, 20);
    private final JLabel pendingFileLabel = new JLabel("Keine Datei");

    private final JComboBox<Teacher> approvedTeacherFilterCombo = new JComboBox<>(approvedTeacherFilterModel);
    private final JComboBox<String> approvedSubjectFilterCombo = new JComboBox<>(approvedSubjectFilterModel);
    private final JComboBox<String> approvedClassFilterCombo = new JComboBox<>(approvedClassFilterModel);
    private final JList<UploadEntry> approvedList = new JList<>(approvedListModel);
    private final JTextField approvedTitleField = new JTextField();
    private final JTextField approvedYearField = new JTextField();
    private final JTextField approvedClassField = new JTextField();
    private final JTextField approvedSubjectField = new JTextField();
    private final JComboBox<String> approvedTypeCombo = new JComboBox<>(new String[]{"Klassenarbeit", "Test"});
    private final JTextArea approvedNoteArea = new JTextArea(4, 20);
    private final JLabel approvedFileLabel = new JLabel("Keine Datei");

    public SchularchivAdmin(Path projectRoot) throws IOException {
        this.stateStore = new StateStore(projectRoot);
        this.state = stateStore.load();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                Path projectRoot = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
                SchularchivAdmin app = new SchularchivAdmin(projectRoot);
                app.show();
            } catch (Exception error) {
                error.printStackTrace();
                JOptionPane.showMessageDialog(null, error.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void show() {
        JFrame frame = new JFrame("Schularchiv Admin");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(new Dimension(1240, 820));
        frame.setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Lehrer", buildTeacherPanel());
        tabs.addTab("Zur Prüfung", buildPendingPanel());
        tabs.addTab("Freigegeben", buildApprovedPanel());

        frame.setContentPane(tabs);
        refreshAll();
        frame.setVisible(true);
    }

    private JPanel buildTeacherPanel() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel form = new JPanel(new GridLayout(0, 2, 12, 12));
        form.add(new JLabel("Lehrer zum Bearbeiten"));
        form.add(teacherEditorCombo);
        form.add(new JLabel("Name"));
        form.add(teacherNameField);
        form.add(new JLabel("Kürzel"));
        form.add(teacherCodeField);
        form.add(new JLabel("Fächer (Komma getrennt)"));
        form.add(teacherSubjectsField);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton newButton = new JButton("Neu");
        JButton saveButton = new JButton("Speichern");
        JButton deleteButton = new JButton("Löschen");
        actions.add(newButton);
        actions.add(saveButton);
        actions.add(deleteButton);

        teacherEditorCombo.addActionListener(event -> loadTeacherIntoForm((Teacher) teacherEditorCombo.getSelectedItem()));
        newButton.addActionListener(event -> {
            teacherEditorCombo.setSelectedItem(null);
            clearTeacherForm();
        });
        saveButton.addActionListener(event -> saveTeacher());
        deleteButton.addActionListener(event -> deleteTeacher());

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.add(form);
        container.add(Box.createVerticalStrut(16));
        container.add(actions);
        return wrapScrollable(container);
    }

    private JPanel buildPendingPanel() {
        pendingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pendingList.setCellRenderer(new UploadRenderer());
        pendingList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadPendingIntoForm(pendingList.getSelectedValue());
            }
        });

        JPanel detail = buildUploadDetailPanel(
            pendingTitleField, pendingYearField, pendingClassField, pendingSubjectField, pendingTypeCombo, pendingNoteArea, pendingFileLabel,
            "Änderungen speichern", this::savePendingChanges,
            "Datei ersetzen", this::replacePendingFile,
            "Freigeben", this::approvePending,
            "Löschen", this::rejectPending
        );

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(pendingList), detail);
        split.setResizeWeight(0.42);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildApprovedPanel() {
        approvedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        approvedList.setCellRenderer(new UploadRenderer());
        approvedList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadApprovedIntoForm(approvedList.getSelectedValue());
            }
        });

        approvedTeacherFilterCombo.addActionListener(event -> refreshApprovedFiltersAfterTeacherChange());
        approvedSubjectFilterCombo.addActionListener(event -> refreshApprovedList());
        approvedClassFilterCombo.addActionListener(event -> refreshApprovedList());

        JPanel filters = new JPanel(new GridLayout(1, 3, 12, 12));
        filters.add(labeled("Lehrer", approvedTeacherFilterCombo));
        filters.add(labeled("Fach", approvedSubjectFilterCombo));
        filters.add(labeled("Klasse", approvedClassFilterCombo));

        JPanel detail = buildUploadDetailPanel(
            approvedTitleField, approvedYearField, approvedClassField, approvedSubjectField, approvedTypeCombo, approvedNoteArea, approvedFileLabel,
            "Änderungen speichern", this::saveApprovedChanges,
            "Datei ersetzen", this::replaceApprovedFile,
            "Zur Prüfung zurück", this::moveApprovedBack,
            "Löschen", this::deleteApproved
        );

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(approvedList), detail);
        split.setResizeWeight(0.42);

        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        panel.add(filters, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildUploadDetailPanel(
        JTextField titleField,
        JTextField yearField,
        JTextField classField,
        JTextField subjectField,
        JComboBox<String> typeCombo,
        JTextArea noteArea,
        JLabel fileLabel,
        String saveLabel,
        Runnable saveAction,
        String replaceFileLabel,
        Runnable replaceFileAction,
        String primaryLabel,
        Runnable primaryAction,
        String secondaryLabel,
        Runnable secondaryAction
    ) {
        JPanel form = new JPanel(new GridLayout(0, 2, 12, 12));
        form.add(new JLabel("Titel"));
        form.add(titleField);
        form.add(new JLabel("Jahr"));
        form.add(yearField);
        form.add(new JLabel("Klasse"));
        form.add(classField);
        form.add(new JLabel("Fach"));
        form.add(subjectField);
        form.add(new JLabel("Art"));
        form.add(typeCombo);
        form.add(new JLabel("Datei"));
        form.add(fileLabel);

        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);

        JPanel notePanel = new JPanel(new BorderLayout());
        notePanel.add(new JLabel("Hinweis"), BorderLayout.NORTH);
        notePanel.add(new JScrollPane(noteArea), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton saveButton = new JButton(saveLabel);
        JButton replaceButton = new JButton(replaceFileLabel);
        JButton primaryButton = new JButton(primaryLabel);
        JButton secondaryButton = new JButton(secondaryLabel);
        actions.add(saveButton);
        actions.add(replaceButton);
        actions.add(primaryButton);
        actions.add(secondaryButton);

        saveButton.addActionListener(event -> saveAction.run());
        replaceButton.addActionListener(event -> replaceFileAction.run());
        primaryButton.addActionListener(event -> primaryAction.run());
        secondaryButton.addActionListener(event -> secondaryAction.run());

        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        panel.add(form, BorderLayout.NORTH);
        panel.add(notePanel, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel wrapScrollable(JPanel content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(content), BorderLayout.CENTER);
        return panel;
    }

    private JPanel labeled(String text, JComboBox<?> comboBox) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel(text), BorderLayout.NORTH);
        panel.add(comboBox, BorderLayout.CENTER);
        return panel;
    }

    private void refreshAll() {
        refreshTeacherEditor();
        refreshPendingList();
        refreshApprovedFilters();
        refreshApprovedList();
    }

    private void refreshTeacherEditor() {
        Teacher previous = (Teacher) teacherEditorCombo.getSelectedItem();
        teacherEditorModel.removeAllElements();
        teacherEditorModel.addElement(null);
        for (Teacher teacher : state.teachersSorted()) {
            teacherEditorModel.addElement(teacher);
        }
        if (previous != null) {
            selectTeacherInCombo(teacherEditorCombo, previous.id);
        } else if (!adminTeacherEditorId.isBlank()) {
            selectTeacherInCombo(teacherEditorCombo, adminTeacherEditorId);
        }
        if (teacherEditorCombo.getSelectedItem() == null) {
            clearTeacherForm();
        }
    }

    private void refreshPendingList() {
        UploadEntry selected = pendingList.getSelectedValue();
        pendingListModel.clear();
        for (UploadEntry entry : state.pendingSortedOldestFirst()) {
            pendingListModel.addElement(entry);
        }
        reselectUpload(pendingList, pendingListModel, selected);
        if (pendingListModel.isEmpty()) {
            clearPendingForm();
        }
    }

    private void refreshApprovedFilters() {
        Teacher selectedTeacher = (Teacher) approvedTeacherFilterCombo.getSelectedItem();
        approvedTeacherFilterModel.removeAllElements();
        approvedTeacherFilterModel.addElement(null);
        for (Teacher teacher : state.teachersSorted()) {
            approvedTeacherFilterModel.addElement(teacher);
        }
        if (selectedTeacher != null) {
            selectTeacherInCombo(approvedTeacherFilterCombo, selectedTeacher.id);
        } else if (!adminTeacherFilter.isBlank()) {
            selectTeacherInCombo(approvedTeacherFilterCombo, adminTeacherFilter);
        }
        refreshApprovedFiltersAfterTeacherChange();
    }

    private void refreshApprovedFiltersAfterTeacherChange() {
        Teacher teacher = (Teacher) approvedTeacherFilterCombo.getSelectedItem();
        adminTeacherFilter = teacher == null ? "" : teacher.id;

        String previousSubject = (String) approvedSubjectFilterCombo.getSelectedItem();
        approvedSubjectFilterModel.removeAllElements();
        approvedSubjectFilterModel.addElement("");
        if (teacher != null) {
            for (String subject : teacher.subjects) {
                approvedSubjectFilterModel.addElement(subject);
            }
        }
        approvedSubjectFilterCombo.setSelectedItem(previousSubject != null ? previousSubject : "");
        if (approvedSubjectFilterCombo.getSelectedItem() == null) {
            approvedSubjectFilterCombo.setSelectedItem("");
        }
        adminSubjectFilter = String.valueOf(approvedSubjectFilterCombo.getSelectedItem());

        String previousClass = (String) approvedClassFilterCombo.getSelectedItem();
        approvedClassFilterModel.removeAllElements();
        approvedClassFilterModel.addElement("");
        for (String classLevel : CLASS_LEVELS) {
            approvedClassFilterModel.addElement(classLevel);
        }
        approvedClassFilterCombo.setSelectedItem(previousClass != null ? previousClass : "");
        if (approvedClassFilterCombo.getSelectedItem() == null) {
            approvedClassFilterCombo.setSelectedItem("");
        }
        adminClassFilter = String.valueOf(approvedClassFilterCombo.getSelectedItem());

        refreshApprovedList();
    }

    private void refreshApprovedList() {
        adminSubjectFilter = valueOrEmpty((String) approvedSubjectFilterCombo.getSelectedItem());
        adminClassFilter = valueOrEmpty((String) approvedClassFilterCombo.getSelectedItem());

        UploadEntry selected = approvedList.getSelectedValue();
        approvedListModel.clear();

        if (!adminTeacherFilter.isBlank() && !adminSubjectFilter.isBlank() && !adminClassFilter.isBlank()) {
            for (UploadEntry entry : state.approvedFiltered(adminTeacherFilter, adminSubjectFilter, adminClassFilter)) {
                approvedListModel.addElement(entry);
            }
        }

        reselectUpload(approvedList, approvedListModel, selected);
        if (approvedListModel.isEmpty()) {
            clearApprovedForm();
        }
    }

    private void loadTeacherIntoForm(Teacher teacher) {
        if (teacher == null) {
            adminTeacherEditorId = "";
            clearTeacherForm();
            return;
        }
        adminTeacherEditorId = teacher.id;
        teacherNameField.setText(teacher.name);
        teacherCodeField.setText(teacher.code);
        teacherSubjectsField.setText(String.join(", ", teacher.subjects));
    }

    private void clearTeacherForm() {
        teacherNameField.setText("");
        teacherCodeField.setText("");
        teacherSubjectsField.setText("");
    }

    private void saveTeacher() {
        String name = teacherNameField.getText().trim();
        String code = teacherCodeField.getText().trim().toUpperCase(Locale.ROOT);
        List<String> subjects = splitSubjects(teacherSubjectsField.getText());
        if (name.isBlank() || code.isBlank() || subjects.isEmpty()) {
            showError("Bitte Name, Kürzel und mindestens ein Fach angeben.");
            return;
        }

        Teacher selected = (Teacher) teacherEditorCombo.getSelectedItem();
        for (Teacher teacher : state.teachers) {
            if (teacher.code.equals(code) && (selected == null || !teacher.id.equals(selected.id))) {
                showError("Dieses Kürzel gibt es bereits.");
                return;
            }
        }

        if (selected == null) {
            state.teachers.add(new Teacher(UUID.randomUUID().toString(), name, code, subjects));
        } else {
            selected.name = name;
            selected.code = code;
            selected.subjects = subjects;
        }

        persist("Teacher gespeichert");
        adminTeacherEditorId = selected == null ? findTeacherByCode(code).id : selected.id;
        refreshAll();
    }

    private void deleteTeacher() {
        Teacher selected = (Teacher) teacherEditorCombo.getSelectedItem();
        if (selected == null) {
            showError("Bitte zuerst einen Lehrer auswählen.");
            return;
        }
        if (state.isTeacherUsed(selected.id)) {
            showError("Dieser Lehrer wird noch in Uploads verwendet und kann nicht gelöscht werden.");
            return;
        }
        if (!confirm("Soll " + selected.name + " wirklich gelöscht werden?")) {
            return;
        }

        state.teachers.removeIf(teacher -> teacher.id.equals(selected.id));
        adminTeacherEditorId = "";
        persist("Teacher gelöscht");
        refreshAll();
    }

    private void loadPendingIntoForm(UploadEntry entry) {
        if (entry == null) {
            clearPendingForm();
            return;
        }
        pendingTitleField.setText(entry.title);
        pendingYearField.setText(entry.year);
        pendingClassField.setText(entry.classLevel);
        pendingSubjectField.setText(entry.subject);
        pendingTypeCombo.setSelectedItem(entry.type);
        pendingNoteArea.setText(entry.note);
        pendingFileLabel.setText(entry.fileName);
    }

    private void clearPendingForm() {
        pendingTitleField.setText("");
        pendingYearField.setText("");
        pendingClassField.setText("");
        pendingSubjectField.setText("");
        pendingTypeCombo.setSelectedItem("Klassenarbeit");
        pendingNoteArea.setText("");
        pendingFileLabel.setText("Keine Datei");
    }

    private void savePendingChanges() {
        UploadEntry entry = pendingList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen Upload auswählen.");
            return;
        }
        applyUploadForm(entry, pendingTitleField, pendingYearField, pendingClassField, pendingSubjectField, pendingTypeCombo, pendingNoteArea);
        persist("Prüfungs-Upload geändert");
        refreshAll();
    }

    private void replacePendingFile() {
        UploadEntry entry = pendingList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen Upload auswählen.");
            return;
        }
        replaceUploadFile(entry);
        persist("Prüfungs-Upload Datei ersetzt");
        refreshAll();
    }

    private void approvePending() {
        UploadEntry entry = pendingList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen Upload auswählen.");
            return;
        }
        state.pendingUploads.remove(entry);
        state.approvedUploads.add(entry);
        persist("Upload freigegeben");
        refreshAll();
    }

    private void rejectPending() {
        UploadEntry entry = pendingList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen Upload auswählen.");
            return;
        }
        if (!confirm("Soll dieser Upload wirklich gelöscht werden?")) {
            return;
        }
        state.pendingUploads.remove(entry);
        persist("Upload gelöscht");
        refreshAll();
    }

    private void loadApprovedIntoForm(UploadEntry entry) {
        if (entry == null) {
            clearApprovedForm();
            return;
        }
        approvedTitleField.setText(entry.title);
        approvedYearField.setText(entry.year);
        approvedClassField.setText(entry.classLevel);
        approvedSubjectField.setText(entry.subject);
        approvedTypeCombo.setSelectedItem(entry.type);
        approvedNoteArea.setText(entry.note);
        approvedFileLabel.setText(entry.fileName);
    }

    private void clearApprovedForm() {
        approvedTitleField.setText("");
        approvedYearField.setText("");
        approvedClassField.setText("");
        approvedSubjectField.setText("");
        approvedTypeCombo.setSelectedItem("Klassenarbeit");
        approvedNoteArea.setText("");
        approvedFileLabel.setText("Keine Datei");
    }

    private void saveApprovedChanges() {
        UploadEntry entry = approvedList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen freigegebenen Eintrag auswählen.");
            return;
        }
        applyUploadForm(entry, approvedTitleField, approvedYearField, approvedClassField, approvedSubjectField, approvedTypeCombo, approvedNoteArea);
        persist("Freigegebener Eintrag geändert");
        refreshAll();
    }

    private void replaceApprovedFile() {
        UploadEntry entry = approvedList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen freigegebenen Eintrag auswählen.");
            return;
        }
        replaceUploadFile(entry);
        persist("Freigegebene Datei ersetzt");
        refreshAll();
    }

    private void moveApprovedBack() {
        UploadEntry entry = approvedList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen freigegebenen Eintrag auswählen.");
            return;
        }
        state.approvedUploads.remove(entry);
        state.pendingUploads.add(entry);
        persist("Freigegebener Eintrag zurückgestellt");
        refreshAll();
    }

    private void deleteApproved() {
        UploadEntry entry = approvedList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen freigegebenen Eintrag auswählen.");
            return;
        }
        if (!confirm("Soll dieser freigegebene Eintrag wirklich gelöscht werden?")) {
            return;
        }
        state.approvedUploads.remove(entry);
        persist("Freigegebener Eintrag gelöscht");
        refreshAll();
    }

    private void applyUploadForm(
        UploadEntry entry,
        JTextField titleField,
        JTextField yearField,
        JTextField classField,
        JTextField subjectField,
        JComboBox<String> typeCombo,
        JTextArea noteArea
    ) {
        entry.title = titleField.getText().trim();
        entry.year = yearField.getText().trim();
        entry.classLevel = classField.getText().trim();
        entry.subject = subjectField.getText().trim();
        entry.type = String.valueOf(typeCombo.getSelectedItem());
        entry.note = noteArea.getText().trim();
    }

    private void replaceUploadFile(UploadEntry entry) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Teacher teacher = state.findTeacher(entry.teacherId);
        if (teacher == null) {
            showError("Der zugehörige Lehrer wurde nicht gefunden.");
            return;
        }

        try {
            Path target = stateStore.copyUploadFile(chooser.getSelectedFile().toPath(), teacher.code, entry.subject, entry.classLevel, entry.year);
            entry.fileName = chooser.getSelectedFile().getName();
            entry.filePath = stateStore.relativeToProject(target).replace('\\', '/');
            entry.previewUrl = stateStore.buildPreviewUrl(entry.filePath);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    private void persist(String successMessage) {
        try {
            stateStore.save(state);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    private Teacher findTeacherByCode(String code) {
        return state.teachers.stream().filter(teacher -> teacher.code.equals(code)).findFirst().orElseThrow();
    }

    private List<String> splitSubjects(String raw) {
        List<String> subjects = new ArrayList<>();
        for (String value : raw.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                subjects.add(trimmed);
            }
        }
        return subjects;
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(null, message, "Bestätigen", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "Fehler", JOptionPane.ERROR_MESSAGE);
    }

    private void selectTeacherInCombo(JComboBox<Teacher> comboBox, String teacherId) {
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            Teacher teacher = comboBox.getItemAt(i);
            if (teacher != null && teacher.id.equals(teacherId)) {
                comboBox.setSelectedIndex(i);
                return;
            }
        }
        comboBox.setSelectedItem(null);
    }

    private void reselectUpload(JList<UploadEntry> list, DefaultListModel<UploadEntry> model, UploadEntry selected) {
        if (selected == null) {
            if (!model.isEmpty()) {
                list.setSelectedIndex(0);
            }
            return;
        }
        for (int index = 0; index < model.getSize(); index++) {
            if (model.getElementAt(index).id.equals(selected.id)) {
                list.setSelectedIndex(index);
                return;
            }
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class UploadRenderer extends DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof UploadEntry entry) {
                label.setText(entry.type + " | " + entry.title + " | " + entry.subject + " | Klasse " + entry.classLevel + " | Jahr " + entry.year);
            }
            return label;
        }
    }

    private static final class StateStore {
        private final Path projectRoot;
        private final Path statePath;

        private StateStore(Path projectRoot) {
            this.projectRoot = projectRoot;
            this.statePath = projectRoot.resolve("data").resolve("state.json");
        }

        private State load() throws IOException {
            if (!Files.exists(statePath)) {
                Files.createDirectories(statePath.getParent());
                State empty = State.empty();
                save(empty);
                return empty;
            }
            String json = Files.readString(statePath, StandardCharsets.UTF_8);
            Object parsed = Json.parse(json);
            return State.fromMap((Map<String, Object>) parsed);
        }

        private void save(State state) throws IOException {
            Files.createDirectories(statePath.getParent());
            Files.writeString(statePath, Json.stringify(state.toMap()), StandardCharsets.UTF_8);
        }

        private Path copyUploadFile(Path source, String teacherCode, String subject, String classLevel, String year) throws IOException {
            String extension = getExtension(source.getFileName().toString());
            String safeTeacher = slugify(teacherCode);
            String safeSubject = slugify(subject);
            String safeName = slugify(stripExtension(source.getFileName().toString()));
            String timestamp = Instant.now().toString().replace(":", "-");
            Path targetDir = projectRoot.resolve("uploads").resolve(year).resolve(safeTeacher).resolve(safeSubject);
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(timestamp + "-" + safeName + extension);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        }

        private String buildPreviewUrl(String relativePath) {
            String remote = detectRemoteUrl();
            String branch = detectBranch();
            if (remote == null || branch == null) {
                return relativePath;
            }
            String cleaned = remote.replace("https://github.com/", "").replace(".git", "");
            return "https://raw.githubusercontent.com/" + cleaned + "/" + branch + "/" + relativePath;
        }

        private String relativeToProject(Path path) {
            return projectRoot.relativize(path.toAbsolutePath().normalize()).toString();
        }

        private String detectRemoteUrl() {
            try {
                Process process = new ProcessBuilder("git", "remote", "get-url", "origin").directory(projectRoot.toFile()).start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                process.waitFor();
                return output.isBlank() ? null : output;
            } catch (Exception error) {
                return null;
            }
        }

        private String detectBranch() {
            try {
                Process process = new ProcessBuilder("git", "branch", "--show-current").directory(projectRoot.toFile()).start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                process.waitFor();
                return output.isBlank() ? "main" : output;
            } catch (Exception error) {
                return "main";
            }
        }

        private String slugify(String value) {
            return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        }

        private String stripExtension(String name) {
            int dot = name.lastIndexOf('.');
            return dot >= 0 ? name.substring(0, dot) : name;
        }

        private String getExtension(String name) {
            int dot = name.lastIndexOf('.');
            return dot >= 0 ? name.substring(dot) : "";
        }
    }

    private static final class State {
        private final List<Teacher> teachers;
        private final List<UploadEntry> pendingUploads;
        private final List<UploadEntry> approvedUploads;

        private State(List<Teacher> teachers, List<UploadEntry> pendingUploads, List<UploadEntry> approvedUploads) {
            this.teachers = teachers;
            this.pendingUploads = pendingUploads;
            this.approvedUploads = approvedUploads;
        }

        private static State empty() {
            return new State(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        private static State fromMap(Map<String, Object> map) {
            return new State(
                Teacher.fromList((List<Object>) map.getOrDefault("teachers", List.of())),
                UploadEntry.fromList((List<Object>) map.getOrDefault("pendingUploads", List.of())),
                UploadEntry.fromList((List<Object>) map.getOrDefault("approvedUploads", List.of()))
            );
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("teachers", teachers.stream().map(Teacher::toMap).toList());
            map.put("pendingUploads", pendingUploads.stream().map(UploadEntry::toMap).toList());
            map.put("approvedUploads", approvedUploads.stream().map(UploadEntry::toMap).toList());
            return map;
        }

        private List<Teacher> teachersSorted() {
            return teachers.stream().sorted((left, right) -> left.code.compareToIgnoreCase(right.code)).toList();
        }

        private List<UploadEntry> pendingSortedOldestFirst() {
            return pendingUploads.stream().sorted((left, right) -> left.uploadedAt.compareTo(right.uploadedAt)).toList();
        }

        private List<UploadEntry> approvedFiltered(String teacherId, String subject, String classLevel) {
            return approvedUploads.stream()
                .filter(entry -> entry.teacherId.equals(teacherId) && entry.subject.equals(subject) && entry.classLevel.equals(classLevel))
                .sorted((left, right) -> left.type.equals(right.type) ? right.year.compareTo(left.year) : ("Klassenarbeit".equals(left.type) ? -1 : 1))
                .toList();
        }

        private boolean isTeacherUsed(String teacherId) {
            return pendingUploads.stream().anyMatch(entry -> entry.teacherId.equals(teacherId))
                || approvedUploads.stream().anyMatch(entry -> entry.teacherId.equals(teacherId));
        }

        private Teacher findTeacher(String teacherId) {
            return teachers.stream().filter(teacher -> teacher.id.equals(teacherId)).findFirst().orElse(null);
        }
    }

    private static final class Teacher {
        private final String id;
        private String name;
        private String code;
        private List<String> subjects;

        private Teacher(String id, String name, String code, List<String> subjects) {
            this.id = id;
            this.name = name;
            this.code = code;
            this.subjects = new ArrayList<>(subjects);
        }

        private static List<Teacher> fromList(List<Object> values) {
            List<Teacher> teachers = new ArrayList<>();
            for (Object value : values) {
                Map<String, Object> map = (Map<String, Object>) value;
                teachers.add(new Teacher(
                    String.valueOf(map.get("id")),
                    String.valueOf(map.get("name")),
                    String.valueOf(map.get("code")),
                    ((List<Object>) map.getOrDefault("subjects", List.of())).stream().map(String::valueOf).toList()
                ));
            }
            return teachers;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("name", name);
            map.put("code", code);
            map.put("subjects", new ArrayList<>(subjects));
            return map;
        }

        @Override
        public String toString() {
            return name + " (" + code + ")";
        }
    }

    private static final class UploadEntry {
        private final String id;
        private final String teacherId;
        private String subject;
        private String classLevel;
        private String type;
        private String year;
        private String title;
        private String note;
        private String fileName;
        private String filePath;
        private String previewUrl;
        private final String uploadedAt;

        private UploadEntry(String id, String teacherId, String subject, String classLevel, String type, String year, String title, String note, String fileName, String filePath, String previewUrl, String uploadedAt) {
            this.id = id;
            this.teacherId = teacherId;
            this.subject = subject;
            this.classLevel = classLevel;
            this.type = type;
            this.year = year;
            this.title = title;
            this.note = note;
            this.fileName = fileName;
            this.filePath = filePath;
            this.previewUrl = previewUrl;
            this.uploadedAt = uploadedAt;
        }

        private static List<UploadEntry> fromList(List<Object> values) {
            List<UploadEntry> uploads = new ArrayList<>();
            for (Object value : values) {
                Map<String, Object> map = (Map<String, Object>) value;
                uploads.add(new UploadEntry(
                    String.valueOf(map.get("id")),
                    String.valueOf(map.get("teacherId")),
                    String.valueOf(map.getOrDefault("subject", "")),
                    String.valueOf(map.getOrDefault("classLevel", "")),
                    String.valueOf(map.getOrDefault("type", "")),
                    String.valueOf(map.getOrDefault("year", "")),
                    String.valueOf(map.getOrDefault("title", "")),
                    String.valueOf(map.getOrDefault("note", "")),
                    String.valueOf(map.getOrDefault("fileName", "")),
                    String.valueOf(map.getOrDefault("filePath", "")),
                    String.valueOf(map.getOrDefault("previewUrl", "")),
                    String.valueOf(map.getOrDefault("uploadedAt", ""))
                ));
            }
            return uploads;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("teacherId", teacherId);
            map.put("subject", subject);
            map.put("classLevel", classLevel);
            map.put("type", type);
            map.put("year", year);
            map.put("title", title);
            map.put("note", note);
            map.put("fileName", fileName);
            map.put("filePath", filePath);
            map.put("previewUrl", previewUrl);
            map.put("uploadedAt", uploadedAt);
            return map;
        }
    }

    private static final class Json {
        private final String text;
        private int index;

        private Json(String text) {
            this.text = text;
        }

        private static Object parse(String text) {
            return new Json(text).readValue();
        }

        private static String stringify(Object value) {
            StringBuilder builder = new StringBuilder();
            writeValue(builder, value, 0);
            return builder.toString();
        }

        private Object readValue() {
            skipWhitespace();
            char current = peek();
            return switch (current) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return map;
            }
            while (true) {
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                map.put(key, value);
                skipWhitespace();
                if (peek() == '}') {
                    index++;
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                if (peek() == ']') {
                    index++;
                    return list;
                }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == '"') {
                    return builder.toString();
                }
                if (current == '\\') {
                    char escaped = text.charAt(index++);
                    builder.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> (char) Integer.parseInt(text.substring(index, index += 4), 16);
                        default -> throw new IllegalStateException("Ungültiges Escape");
                    });
                } else {
                    builder.append(current);
                }
            }
            throw new IllegalStateException("String nicht abgeschlossen");
        }

        private Object readNumber() {
            int start = index;
            while (index < text.length() && "-0123456789.eE+".indexOf(text.charAt(index)) >= 0) {
                index++;
            }
            String raw = text.substring(start, index);
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                return Double.parseDouble(raw);
            }
            return Long.parseLong(raw);
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) {
                throw new IllegalStateException("Ungültiges Literal");
            }
            index += literal.length();
            return value;
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private char peek() {
            skipWhitespace();
            return text.charAt(index);
        }

        private void expect(char expected) {
            skipWhitespace();
            if (text.charAt(index) != expected) {
                throw new IllegalStateException("Erwartet: " + expected);
            }
            index++;
        }

        private static void writeValue(StringBuilder builder, Object value, int indent) {
            if (value == null) {
                builder.append("null");
                return;
            }
            if (value instanceof String text) {
                builder.append('"').append(escape(text)).append('"');
                return;
            }
            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
                return;
            }
            if (value instanceof Map<?, ?> map) {
                builder.append("{\n");
                int count = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    indent(builder, indent + 2);
                    builder.append('"').append(escape(String.valueOf(entry.getKey()))).append("\": ");
                    writeValue(builder, entry.getValue(), indent + 2);
                    if (++count < map.size()) {
                        builder.append(',');
                    }
                    builder.append('\n');
                }
                indent(builder, indent);
                builder.append('}');
                return;
            }
            if (value instanceof List<?> list) {
                builder.append("[\n");
                for (int index = 0; index < list.size(); index++) {
                    indent(builder, indent + 2);
                    writeValue(builder, list.get(index), indent + 2);
                    if (index + 1 < list.size()) {
                        builder.append(',');
                    }
                    builder.append('\n');
                }
                indent(builder, indent);
                builder.append(']');
                return;
            }
            throw new IllegalStateException("Nicht unterstützter Typ");
        }

        private static void indent(StringBuilder builder, int size) {
            builder.append(" ".repeat(size));
        }

        private static String escape(String value) {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        }
    }
}
