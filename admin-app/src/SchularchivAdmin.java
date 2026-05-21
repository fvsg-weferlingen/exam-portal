import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
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
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SchularchivAdmin {
    private static final String[] CLASS_LEVELS = {"5", "6", "7", "8", "9", "10", "11", "12"};
    private static final String DEFAULT_API_BASE_URL = "https://exam-portal-plum-phi.vercel.app";

    private final ApiClient apiClient;
    private ArchiveState state = ArchiveState.empty();

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
    private final JLabel pendingPreviewLabel = createPreviewLabel();
    private final JButton pendingDownloadButton = new JButton("Datei herunterladen");
    private final JButton pendingOpenButton = new JButton("Datei öffnen");

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
    private final JLabel approvedPreviewLabel = createPreviewLabel();
    private final JButton approvedDownloadButton = new JButton("Datei herunterladen");
    private final JButton approvedOpenButton = new JButton("Datei öffnen");

    public SchularchivAdmin(String apiBaseUrl) {
        this.apiClient = new ApiClient(apiBaseUrl);
        pendingDownloadButton.setEnabled(false);
        approvedDownloadButton.setEnabled(false);
        pendingOpenButton.setEnabled(false);
        approvedOpenButton.setEnabled(false);
        pendingDownloadButton.addActionListener(event -> downloadUploadFile(pendingList.getSelectedValue()));
        approvedDownloadButton.addActionListener(event -> downloadUploadFile(approvedList.getSelectedValue()));
        pendingOpenButton.addActionListener(event -> openUploadFile(pendingList.getSelectedValue()));
        approvedOpenButton.addActionListener(event -> openUploadFile(approvedList.getSelectedValue()));
        log("Admin-App vorbereitet. API: " + apiBaseUrl);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                String apiBaseUrl = args.length > 0 ? args[0] : DEFAULT_API_BASE_URL;
                SchularchivAdmin app = new SchularchivAdmin(apiBaseUrl);
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
        frame.setVisible(true);
        refreshStateAsync();
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

        JPanel container = new JPanel(new BorderLayout(0, 16));
        container.add(form, BorderLayout.NORTH);
        container.add(actions, BorderLayout.SOUTH);
        panel.add(container, BorderLayout.NORTH);
        return panel;
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
            pendingTitleField, pendingYearField, pendingClassField, pendingSubjectField, pendingTypeCombo, pendingNoteArea, pendingFileLabel, pendingPreviewLabel, pendingDownloadButton, pendingOpenButton,
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
            approvedTitleField, approvedYearField, approvedClassField, approvedSubjectField, approvedTypeCombo, approvedNoteArea, approvedFileLabel, approvedPreviewLabel, approvedDownloadButton, approvedOpenButton,
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
        JLabel previewLabel,
        JButton downloadButton,
        JButton openButton,
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
        noteArea.setRows(2);

        JPanel notePanel = new JPanel(new BorderLayout());
        notePanel.add(new JLabel("Hinweis"), BorderLayout.NORTH);
        JScrollPane noteScrollPane = new JScrollPane(noteArea);
        noteScrollPane.setPreferredSize(new Dimension(360, 72));
        notePanel.add(noteScrollPane, BorderLayout.CENTER);

        JPanel previewPanel = new JPanel(new BorderLayout(0, 8));
        previewPanel.add(new JLabel("Vorschau"), BorderLayout.NORTH);
        previewPanel.add(new JScrollPane(previewLabel), BorderLayout.CENTER);
        JPanel previewActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        previewActions.add(downloadButton);
        previewActions.add(new JLabel("  "));
        previewActions.add(openButton);
        previewPanel.add(previewActions, BorderLayout.SOUTH);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 16));
        centerPanel.add(notePanel, BorderLayout.NORTH);
        centerPanel.add(previewPanel, BorderLayout.CENTER);

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
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JLabel createPreviewLabel() {
        JLabel label = new JLabel("Keine Vorschau verfÃ¼gbar", JLabel.CENTER);
        label.setVerticalAlignment(JLabel.CENTER);
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setOpaque(true);
        label.setBackground(new Color(245, 247, 250));
        label.setPreferredSize(new Dimension(360, 280));
        return label;
    }

    private JPanel labeled(String text, JComboBox<?> comboBox) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel(text), BorderLayout.NORTH);
        panel.add(comboBox, BorderLayout.CENTER);
        return panel;
    }

    private void refreshStateAsync() {
        log("Lade aktuellen Stand von der API ...");
        new SwingWorker<ArchiveState, Void>() {
            @Override
            protected ArchiveState doInBackground() throws Exception {
                return apiClient.loadState();
            }

            @Override
            protected void done() {
                try {
                    state = get();
                    log("Stand geladen: " + state.teachers.size() + " Lehrer, " + state.pendingUploads.size() + " offene Uploads, " + state.approvedUploads.size() + " freigegebene Uploads.");
                    refreshAll();
                } catch (Exception error) {
                    log("Fehler beim Laden: " + error.getMessage());
                    showError("Daten konnten nicht geladen werden: " + error.getMessage());
                }
            }
        }.execute();
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
        adminSubjectFilter = valueOrEmpty((String) approvedSubjectFilterCombo.getSelectedItem());

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
        adminClassFilter = valueOrEmpty((String) approvedClassFilterCombo.getSelectedItem());

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

        String teacherId = adminTeacherEditorId;
        runActionAsync("saveTeacher", Map.of(
            "teacherId", teacherId,
            "name", name,
            "code", code,
            "subjects", subjects
        ), () -> {
            if (teacherId.isBlank()) {
                Teacher created = state.findTeacherByCode(code);
                adminTeacherEditorId = created == null ? "" : created.id;
            }
        });
    }

    private void deleteTeacher() {
        Teacher selected = (Teacher) teacherEditorCombo.getSelectedItem();
        if (selected == null) {
            showError("Bitte zuerst einen Lehrer auswählen.");
            return;
        }
        if (!confirm("Soll " + selected.name + " wirklich gelöscht werden?")) {
            return;
        }

        runActionAsync("deleteTeacher", Map.of("teacherId", selected.id), () -> adminTeacherEditorId = "");
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
        pendingDownloadButton.setEnabled(!entry.previewUrl.isBlank());
        pendingOpenButton.setEnabled(!entry.previewUrl.isBlank());
        loadPreviewAsync(entry, pendingPreviewLabel);
    }

    private void clearPendingForm() {
        pendingTitleField.setText("");
        pendingYearField.setText("");
        pendingClassField.setText("");
        pendingSubjectField.setText("");
        pendingTypeCombo.setSelectedItem("Klassenarbeit");
        pendingNoteArea.setText("");
        pendingFileLabel.setText("Keine Datei");
        pendingDownloadButton.setEnabled(false);
        pendingOpenButton.setEnabled(false);
        clearPreview(pendingPreviewLabel);
    }

    private void savePendingChanges() {
        UploadEntry entry = pendingList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen Upload auswählen.");
            return;
        }
        runActionAsync("updatePendingUpload", Map.of(
            "uploadId", entry.id,
            "changes", uploadChangesMap(pendingTitleField, pendingYearField, pendingClassField, pendingSubjectField, pendingTypeCombo, pendingNoteArea)
        ), null);
    }

    private void replacePendingFile() {
        UploadEntry entry = pendingList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen Upload auswählen.");
            return;
        }

        FilePayload filePayload = chooseFilePayload();
        if (filePayload == null) {
            return;
        }

        runActionAsync("replacePendingUploadFile", Map.of(
            "uploadId", entry.id,
            "fileName", filePayload.fileName,
            "fileDataUrl", filePayload.dataUrl
        ), null);
    }

    private void approvePending() {
        UploadEntry entry = pendingList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen Upload auswählen.");
            return;
        }
        runActionAsync("approveUpload", Map.of("uploadId", entry.id), null);
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
        runActionAsync("rejectUpload", Map.of("uploadId", entry.id), null);
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
        approvedDownloadButton.setEnabled(!entry.previewUrl.isBlank());
        approvedOpenButton.setEnabled(!entry.previewUrl.isBlank());
        loadPreviewAsync(entry, approvedPreviewLabel);
    }

    private void clearApprovedForm() {
        approvedTitleField.setText("");
        approvedYearField.setText("");
        approvedClassField.setText("");
        approvedSubjectField.setText("");
        approvedTypeCombo.setSelectedItem("Klassenarbeit");
        approvedNoteArea.setText("");
        approvedFileLabel.setText("Keine Datei");
        approvedDownloadButton.setEnabled(false);
        approvedOpenButton.setEnabled(false);
        clearPreview(approvedPreviewLabel);
    }

    private void saveApprovedChanges() {
        UploadEntry entry = approvedList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen freigegebenen Eintrag auswählen.");
            return;
        }
        runActionAsync("updateApprovedUpload", Map.of(
            "uploadId", entry.id,
            "changes", uploadChangesMap(approvedTitleField, approvedYearField, approvedClassField, approvedSubjectField, approvedTypeCombo, approvedNoteArea)
        ), null);
    }

    private void replaceApprovedFile() {
        UploadEntry entry = approvedList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen freigegebenen Eintrag auswählen.");
            return;
        }

        FilePayload filePayload = chooseFilePayload();
        if (filePayload == null) {
            return;
        }

        runActionAsync("replaceApprovedUploadFile", Map.of(
            "uploadId", entry.id,
            "fileName", filePayload.fileName,
            "fileDataUrl", filePayload.dataUrl
        ), null);
    }

    private void moveApprovedBack() {
        UploadEntry entry = approvedList.getSelectedValue();
        if (entry == null) {
            showError("Bitte zuerst einen freigegebenen Eintrag auswählen.");
            return;
        }
        runActionAsync("moveBackToPending", Map.of("uploadId", entry.id), null);
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
        runActionAsync("deleteApprovedUpload", Map.of("uploadId", entry.id), null);
    }

    private Map<String, Object> uploadChangesMap(
        JTextField titleField,
        JTextField yearField,
        JTextField classField,
        JTextField subjectField,
        JComboBox<String> typeCombo,
        JTextArea noteArea
    ) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("title", titleField.getText().trim());
        changes.put("year", yearField.getText().trim());
        changes.put("classLevel", classField.getText().trim());
        changes.put("subject", subjectField.getText().trim());
        changes.put("type", String.valueOf(typeCombo.getSelectedItem()));
        changes.put("note", noteArea.getText().trim());
        return changes;
    }

    private FilePayload chooseFilePayload() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        Path path = chooser.getSelectedFile().toPath();
        try {
            String fileName = chooser.getSelectedFile().getName();
            String mimeType = detectMimeType(path);
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(path));
            return new FilePayload(fileName, "data:" + mimeType + ";base64," + base64);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    private String detectMimeType(Path path) throws IOException {
        String mimeType = Files.probeContentType(path);
        return mimeType == null ? "application/octet-stream" : mimeType;
    }

    private void loadPreviewAsync(UploadEntry entry, JLabel targetLabel) {
        clearPreview(targetLabel);
        if (entry.previewUrl.isBlank()) {
            targetLabel.setText("Keine Vorschau verfÃ¼gbar");
            return;
        }

        log("Lade Vorschau für: " + entry.fileName);
        targetLabel.setText("Bild wird geladen...");
        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                HttpRequest request = HttpRequest.newBuilder(URI.create(entry.previewUrl)).GET().build();
                HttpResponse<InputStream> response = apiClient.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 400) {
                    throw new IOException("Vorschau konnte nicht geladen werden.");
                }
                String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
                if (!contentType.startsWith("image/")) {
                    throw new IOException("Keine Bilddatei");
                }
                try (InputStream stream = response.body()) {
                    BufferedImage image = ImageIO.read(stream);
                    if (image == null) {
                        throw new IOException("Bildformat wird von Java hier nicht direkt unterstÃ¼tzt.");
                    }
                    return new ImageIcon(scaleImage(image, 720, 480));
                }
            }

            @Override
            protected void done() {
                try {
                    targetLabel.setText("");
                    targetLabel.setIcon(get());
                    log("Vorschau geladen: " + entry.fileName);
                } catch (Exception error) {
                    clearPreview(targetLabel);
                    targetLabel.setText("Diese Datei kann hier nicht als Bild angezeigt werden. Bitte herunterladen.");
                    log("Vorschau nicht möglich: " + entry.fileName + " -> " + error.getMessage());
                }
            }
        }.execute();
    }

    private Image scaleImage(BufferedImage image, int maxWidth, int maxHeight) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return image;
        }

        double ratio = Math.min((double) maxWidth / width, (double) maxHeight / height);
        ratio = Math.min(ratio, 1.0d);
        int scaledWidth = Math.max(1, (int) Math.round(width * ratio));
        int scaledHeight = Math.max(1, (int) Math.round(height * ratio));
        return image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
    }

    private void clearPreview(JLabel targetLabel) {
        targetLabel.setIcon(null);
        targetLabel.setText("Keine Vorschau verfÃ¼gbar");
    }

    private void downloadUploadFile(UploadEntry entry) {
        if (entry == null || entry.previewUrl.isBlank()) {
            showError("FÃ¼r diesen Eintrag ist keine Datei verfÃ¼gbar.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(entry.fileName));
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path target = chooser.getSelectedFile().toPath();
        log("Starte Download: " + entry.fileName + " -> " + target);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                downloadToTarget(entry, target);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    log("Download abgeschlossen: " + target);
                    JOptionPane.showMessageDialog(null, "Datei wurde gespeichert.");
                } catch (Exception error) {
                    log("Download fehlgeschlagen: " + error.getMessage());
                    showError("Download fehlgeschlagen: " + error.getMessage());
                }
            }
        }.execute();
    }

    private void openUploadFile(UploadEntry entry) {
        if (entry == null || entry.previewUrl.isBlank()) {
            showError("Für diesen Eintrag ist keine Datei verfügbar.");
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            showError("Dateien können auf diesem System nicht direkt geöffnet werden.");
            return;
        }

        log("Lade Datei zum Öffnen: " + entry.fileName);
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                Path tempFile = Files.createTempFile("schularchiv-", fileSuffix(entry.fileName));
                downloadToTarget(entry, tempFile);
                return tempFile;
            }

            @Override
            protected void done() {
                try {
                    Path tempFile = get();
                    log("Öffne lokale Datei: " + tempFile);
                    Desktop.getDesktop().open(tempFile.toFile());
                } catch (Exception error) {
                    log("Öffnen fehlgeschlagen: " + error.getMessage());
                    showError("Datei konnte nicht geöffnet werden: " + error.getMessage());
                }
            }
        }.execute();
    }

    private void downloadToTarget(UploadEntry entry, Path target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(entry.previewUrl)).GET().build();
        HttpResponse<InputStream> response = apiClient.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IOException("Datei konnte nicht heruntergeladen werden.");
        }
        try (InputStream stream = response.body()) {
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String fileSuffix(String fileName) {
        if (fileName == null) {
            return ".tmp";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return ".tmp";
        }
        return fileName.substring(dotIndex);
    }

    private void runActionAsync(String action, Map<String, Object> payload, Runnable onSuccess) {
        log("Starte Aktion: " + action);
        new SwingWorker<ArchiveState, Void>() {
            @Override
            protected ArchiveState doInBackground() throws Exception {
                return apiClient.runAction(action, payload);
            }

            @Override
            protected void done() {
                try {
                    state = get();
                    log("Aktion erfolgreich: " + action);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    refreshAll();
                } catch (Exception error) {
                    log("Aktion fehlgeschlagen: " + action + " -> " + error.getMessage());
                    showError("Aktion fehlgeschlagen: " + error.getMessage());
                }
            }
        }.execute();
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(null, message, "Bestätigen", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private void log(String message) {
        System.out.println("[" + Instant.now() + "] " + message);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "Fehler", JOptionPane.ERROR_MESSAGE);
    }

    private void selectTeacherInCombo(JComboBox<Teacher> comboBox, String teacherId) {
        for (int index = 0; index < comboBox.getItemCount(); index++) {
            Teacher teacher = comboBox.getItemAt(index);
            if (teacher != null && teacher.id.equals(teacherId)) {
                comboBox.setSelectedIndex(index);
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

    private static final class FilePayload {
        private final String fileName;
        private final String dataUrl;

        private FilePayload(String fileName, String dataUrl) {
            this.fileName = fileName;
            this.dataUrl = dataUrl;
        }
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

    private static final class ApiClient {
        private final HttpClient httpClient = HttpClient.newHttpClient();
        private final String baseUrl;

        private ApiClient(String baseUrl) {
          this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }

        private ArchiveState loadState() throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/state")).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IOException(response.body());
            }
            return ArchiveState.fromMap((Map<String, Object>) Json.parse(response.body()));
        }

        private ArchiveState runAction(String action, Map<String, Object> payload) throws IOException, InterruptedException {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("action", action);
            requestBody.put("payload", payload);

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/action"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(requestBody), StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IOException(response.body());
            }

            Map<String, Object> parsed = (Map<String, Object>) Json.parse(response.body());
            return ArchiveState.fromMap((Map<String, Object>) parsed.get("state"));
        }
    }

    private static final class ArchiveState {
        private final List<Teacher> teachers;
        private final List<UploadEntry> pendingUploads;
        private final List<UploadEntry> approvedUploads;

        private ArchiveState(List<Teacher> teachers, List<UploadEntry> pendingUploads, List<UploadEntry> approvedUploads) {
            this.teachers = teachers;
            this.pendingUploads = pendingUploads;
            this.approvedUploads = approvedUploads;
        }

        private static ArchiveState empty() {
            return new ArchiveState(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        private static ArchiveState fromMap(Map<String, Object> map) {
            return new ArchiveState(
                Teacher.fromList((List<Object>) map.getOrDefault("teachers", List.of())),
                UploadEntry.fromList((List<Object>) map.getOrDefault("pendingUploads", List.of())),
                UploadEntry.fromList((List<Object>) map.getOrDefault("approvedUploads", List.of()))
            );
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

        private Teacher findTeacherByCode(String code) {
            return teachers.stream().filter(teacher -> teacher.code.equals(code)).findFirst().orElse(null);
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
                    String.valueOf(map.getOrDefault("uploadedAt", Instant.EPOCH.toString()))
                ));
            }
            return uploads;
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
