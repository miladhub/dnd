package org.meh.dnd.openai;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpUrlConnectionOpenAiClient
        implements OpenAiClient
{
    private static final Logger LOG = LoggerFactory.getLogger(OpenAiClient.class);
    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String OPENAI_CHAT_MODEL = "gpt-4o-mini";
    private static final int OPENAI_RESPONSE_MAX_TOKENS = 500;

    @Override
    public ChatResponse chatCompletion(
            List<OpenAiRequestMessage> messages,
            List<ModelFunction> functions
    )
    throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            String json = toJson(messages, functions);
            LOG.info("Request:\n{}", json);
            HttpURLConnection con = getHttpURLConnection();
            try (OutputStream os = con.getOutputStream()) {
                LOG.debug("Sending JSON to chat completion API:\n{}", json);
                byte[] bytes = json.getBytes(UTF_8);
                os.write(bytes, 0, bytes.length);
            }
            try (InputStream is = con.getInputStream()) {
                String res = new String(is.readAllBytes(), UTF_8);
                LOG.info("Response:\n{}", res);
                OpenAiResponse resp = jsonb.fromJson(res, OpenAiResponse.class);
                OpenAiChoice choice = resp.choices().getFirst();
                if (!"stop".equals(choice.finish_reason()))
                    LOG.info("Finish reason: {}", choice.finish_reason());
                LOG.debug("Usage: {}", resp.usage());
                OpenAiResponseMessage msg = choice.message();
                if (msg.function_call() != null)
                    return new ChatResponse.FunctionCallChatResponse(
                            msg.function_call().name(),
                            msg.function_call().arguments()
                    );
                else return new ChatResponse.MessageChatResponse(msg.content());
            }
        }
    }

    private static HttpURLConnection getHttpURLConnection()
    throws IOException {
        URL url = URI.create("https://api.openai.com/v1/chat/completions").toURL();
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(true);
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("Authorization", "Bearer " + OPENAI_API_KEY);
        con.setConnectTimeout(300_000);
        con.setReadTimeout(300_000);
        return con;
    }

    private String toJson(
            List<OpenAiRequestMessage> messages,
            List<ModelFunction> functions
    )
    throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            if (functions.isEmpty()) {
                return String.format(
                        """
                        {
                            "model": "%s",
                            "messages": %s,
                            "temperature": 0.7,
                            "max_tokens": %d
                        }
                        """,
                        OPENAI_CHAT_MODEL,
                        jsonb.toJson(messages),
                        OPENAI_RESPONSE_MAX_TOKENS);
            } else {
                return String.format(
                        """
                        {
                            "model": "%s",
                            "messages": %s,
                            "functions": %s,
                            "temperature": 0.7,
                            "max_tokens": %d
                        }
                        """,
                        OPENAI_CHAT_MODEL,
                        jsonb.toJson(messages),
                        "[" + functions.stream()
                                .map(ModelFunction::body)
                                .collect(Collectors.joining(",\n")) + "]",
                        OPENAI_RESPONSE_MAX_TOKENS);
            }
        }
    }
}
