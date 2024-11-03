package org.meh.dnd.openai;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public class HttpUrlConnectionOpenAiClient
        implements OpenAiClient
{
    private static final Logger LOG = LoggerFactory.getLogger(OpenAiClient.class);
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String OPENAI_CHAT_MODEL = "gpt-4o-mini";
    private static final int OPENAI_RESPONSE_MAX_TOKENS = 5000;
    private final JsonbConfig jsonbConfig =
            new JsonbConfig().withFormatting(true);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(300))
            .build();

    @Override
    public ChatResponse chatCompletion(
            List<OpenAiRequestMessage> messages,
            List<ModelFunction> functions
    )
    throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create(jsonbConfig)) {
            String json = toJson(messages, functions);
            LOG.debug("Request:\n{}", json);
            LOG.info("Request content:\n{}",
                    messages.stream()
                            .map(OpenAiRequestMessage::content)
                            .collect(Collectors.joining("\n")));

            LOG.debug("Sending JSON to chat completion API:\n{}", json);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_API_URL))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + OPENAI_API_KEY)
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            String res = response.body();
            LOG.debug("Response:\n{}", res);
            OpenAiResponse resp = jsonb.fromJson(res, OpenAiResponse.class);
            OpenAiChoice choice = resp.choices().getFirst();
            if (!"stop".equals(choice.finish_reason()))
                LOG.info("Finish reason: {}", choice.finish_reason());
            LOG.debug("Usage: {}", resp.usage());
            OpenAiResponseMessage msg = choice.message();
            if (msg.function_call() != null) {
                return new ChatResponse.FunctionCallChatResponse(
                        msg.function_call().name(),
                        msg.function_call().arguments()
                );
            } else {
                LOG.info("Response content:\n{}", msg.content());
                return new ChatResponse.MessageChatResponse(msg.content());
            }
        }
    }

    private String toJson(
            List<OpenAiRequestMessage> messages,
            List<ModelFunction> functions
    )
    throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create(jsonbConfig)) {
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
