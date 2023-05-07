package net.cicchiello.intellij.settingsshare;

import com.intellij.ide.ActivityTracker;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.GridBag;
import net.cicchiello.intellij.settingsshare.action.SyncSettingsTask;
import net.cicchiello.intellij.settingsshare.service.GitRepoService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import java.awt.EventQueue;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PluginSettings implements Configurable, ActionListener {

    private JPanel mainComponent;
    private JTextField repoField;
    private JButton getBranchesButton;
    private ComboBox<String> branchSelector;
    private JCheckBox enforceCheckBox;
    private String initialUrl;

    @Override
    public String getDisplayName() {
        return "Shared Settings";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (mainComponent == null) {
            mainComponent = new JPanel(new GridBagLayout());
            final GridBag mainGridBag = new GridBag();

            final JPanel repoPanel = new JPanel(new GridBagLayout());
            mainComponent.add(repoPanel, mainGridBag.nextLine().next().fillCellHorizontally().weightx(1));
            final GridBag repoGridBag = new GridBag();
            final JLabel repoLabel = new JLabel("Git Repository: ");
            repoPanel.add(repoLabel, repoGridBag.nextLine().next());
            repoField = new JTextField();
            repoField.addActionListener(this);
            repoField.getDocument().addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(@NotNull final DocumentEvent e) {
                    branchSelector.setEnabled(repoField.getText().equals(initialUrl));
                }
            });
            repoPanel.add(repoField, repoGridBag.next().fillCellHorizontally().weightx(1));
            repoLabel.setLabelFor(repoField);
            getBranchesButton = new JButton("Get Branches");
            getBranchesButton.addActionListener(this);
            repoPanel.add(getBranchesButton, repoGridBag.next());

            final JPanel branchPanel = new JPanel(new GridBagLayout());
            mainComponent.add(branchPanel, mainGridBag.nextLine().next().fillCellHorizontally().weightx(1));
            final GridBag branchGridBag = new GridBag();
            final JLabel branchLabel = new JLabel("Branch: ");
            branchPanel.add(branchLabel, branchGridBag.nextLine().next());
            branchSelector = new ComboBox<>();
            branchSelector.setEnabled(false);
            branchPanel.add(branchSelector, branchGridBag.next());
            branchPanel.add(Box.createHorizontalGlue(), branchGridBag.next().fillCellHorizontally().weightx(1));

            final JPanel enforcePanel = new JPanel(new GridBagLayout());
            mainComponent.add(enforcePanel, mainGridBag.nextLine().next().fillCellHorizontally().weightx(1));
            final GridBag enforceGridBag = new GridBag();
            enforceCheckBox = new JCheckBox("Automatically Enforce Shared Settings");
            enforceCheckBox.setEnabled(false);
            enforcePanel.add(enforceCheckBox, enforceGridBag.nextLine().next());
            enforcePanel.add(Box.createHorizontalGlue(), enforceGridBag.next().fillCellHorizontally().weightx(1));

            mainComponent.add(Box.createVerticalGlue(), mainGridBag.nextLine().next().weighty(1));
        }
        return mainComponent;
    }

    @Override
    public boolean isModified() {
        final AppSettingsState state = AppSettingsState.getInstance();
        return (repoField.getText().isBlank() && state.repositoryUrl != null)
                || (branchSelector.isEnabled() && (!Objects.equals(state.branch, branchSelector.getItem()) || !Objects.equals(state.repositoryUrl, repoField.getText()) || state.enforceSettings != enforceCheckBox.isSelected()))
                || (Objects.equals(state.branch, branchSelector.getItem()) && Objects.equals(state.repositoryUrl, repoField.getText()) && state.enforceSettings != enforceCheckBox.isSelected());
    }

    @Override
    public void apply() {
        final AppSettingsState state = AppSettingsState.getInstance();
        final String url = repoField.getText();
        state.repositoryUrl = url.isBlank() ? null : url;
        state.branch = state.repositoryUrl == null ? null : (String) branchSelector.getSelectedItem();
        state.enforceSettings = enforceCheckBox.isSelected();
        initialUrl = state.repositoryUrl;
        if (state.branch == null) {
            branchSelector.removeAllItems();
        }
        ActivityTracker.getInstance().inc();
        if (state.repositoryUrl != null && state.branch != null && state.enforceSettings) {
            ApplicationManager.getApplication().invokeLater(() -> {
                final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
                final SyncSettingsTask task = new SyncSettingsTask(openProjects, openProjects[0], false);
                ProgressManager.getInstance().run(task);
            });
        }
    }

    @Override
    public void reset() {
        final AppSettingsState state = AppSettingsState.getInstance();
        repoField.setText(state.repositoryUrl == null ? "" : state.repositoryUrl);
        branchSelector.setEnabled(false);
        enforceCheckBox.setEnabled(state.branch != null && state.repositoryUrl != null);
        branchSelector.removeAllItems();
        if (state.branch != null) {
            branchSelector.addItem(state.branch);
            branchSelector.setSelectedItem(state.branch);
        }
        enforceCheckBox.setSelected(state.enforceSettings);
        initialUrl = null;
    }

    @Override
    public void disposeUIResources() {
        mainComponent = null;
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
        final GitRepoService service = ApplicationManager.getApplication().getService(GitRepoService.class);
        final Optional<Project> maybeProject = Arrays.stream(ProjectManager.getInstance().getOpenProjects())
                .filter(p -> !p.isDisposed())
                .findAny();
        if (maybeProject.isEmpty()) {
            return;
        }
        final Project project = maybeProject.get();
        getBranchesButton.setEnabled(false);
        repoField.setEnabled(false);
        final String url = repoField.getText();
        final Task.Modal modal = new Task.Modal(project, mainComponent, "Getting Branches from Git Repository", false) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
                try {
                    final List<String> branches = service.getBranches(project, url);
                    updateBranches(branches);
                } catch (final IOException ex) {
                    NotificationGroupManager.getInstance()
                            .getNotificationGroup("net.cicchiello.intellij.settingsshare")
                            .createNotification(ex.getMessage(), NotificationType.ERROR)
                            .notify(project);
                } finally {
                    EventQueue.invokeLater(() -> {
                        getBranchesButton.setEnabled(true);
                        repoField.setEnabled(true);
                    });
                }
            }
        };
        ApplicationManager.getApplication().invokeLater(() -> ProgressManager.getInstance().run(modal));
    }

    private void updateBranches(final List<String> branches) {
        final AppSettingsState state = AppSettingsState.getInstance();
        EventQueue.invokeLater(() -> {
            branchSelector.removeAllItems();
            branches.forEach(branchSelector::addItem);
            branchSelector.setEnabled(branches.size() > 0);
            enforceCheckBox.setEnabled(branchSelector.isEnabled());
            if (branches.size() > 0) {
                initialUrl = repoField.getText();
            }
            if (state.branch != null && branches.contains(state.branch)) {
                branchSelector.setItem(state.branch);
            }
        });
    }

}
