package actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.JSONPObject;
import com.fasterxml.jackson.jr.ob.JSONObjectException;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class GenerateCodeAction extends AnAction {

    private static final String OLLAMA_API_URL = "http://localhost:11434/api/generate";
    private static final String MODEL_NAME = "codellama";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);

        if (project != null && editor != null) {
            String selectedText = editor.getSelectionModel().getSelectedText();
            if (selectedText != null && !selectedText.isEmpty()) {
                if (!selectedText.startsWith("//")) {
                    Messages.showInfoMessage(project, "Please select a commented line only.", "Info");
                    return;
                }

                try {
                    String generatedCode = generateCode(selectedText);

                    // Do something with the generated code, like insert it below the selected text in the editor
                    Runnable r = () -> {
                        int offset = editor.getSelectionModel().getSelectionEnd();
                        editor.getDocument().insertString(offset, "\n" + generatedCode);
                    };

                    WriteCommandAction.runWriteCommandAction(project, r);
                } catch (IOException ex) {
                    Messages.showErrorDialog(project, "Error generating code: " + ex.getMessage(), "Error");
                }
            } else {
                Messages.showInfoMessage(project, "Please select some text to generate code from.", "Info");
            }
        }
    }

    private String generateCode(String prompt) throws IOException {
        URL url = new URL(OLLAMA_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        String postData = String.format("{\"model\": \"%s\", \"prompt\": \"%s\"}", MODEL_NAME, prompt);
        connection.getOutputStream().write(postData.getBytes());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            // Split the concatenated JSON string into individual JSON objects
            String[] jsonObjects = response.toString().split("\\}\\{");
            List<String> individualResponses = new ArrayList<>();
            for (String jsonObject : jsonObjects) {
                // Reconstruct each JSON object (add missing braces)
                if (!jsonObject.startsWith("{")) {
                    jsonObject = "{" + jsonObject;
                }
                if (!jsonObject.endsWith("}")) {
                    jsonObject += "}";
                }
                individualResponses.add(jsonObject);
            }

            // Parse each JSON object to extract the "response" field
            ObjectMapper objectMapper = new ObjectMapper();
            StringBuilder responseBuilder = new StringBuilder();
            for (String jsonResponse : individualResponses) {
                JsonNode jsonNode = objectMapper.readTree(jsonResponse);
                JsonNode responseNode = jsonNode.get("response");
                if (responseNode != null && responseNode.isTextual()) {
                    responseBuilder.append(responseNode.asText()).append(" ");
                }
            }
            String output = responseBuilder.toString().trim(); // Trim to remove any leading or trailing whitespace
            //output = output.replaceAll("```", ""); // Remove backticks
            return output;
        } finally {
            connection.disconnect();
}}}
