package org.meh.dnd;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.*;

public record Templates()
{
    public static String template(GameView view) {
        return render("/_template.html", view);
    }

    public static String combat(CombatView view) {
        return render("/_combat.html", view);
    }

    private static String render(
            String html,
            Object view
    ) {
        MustacheFactory mf = new DefaultMustacheFactory();
        try (InputStream is = Templates.class.getResourceAsStream(html)
        ) {
            assert (is != null);
            Reader r = new InputStreamReader(is);
            Mustache template = mf.compile(r, "output");
            StringWriter writer = new StringWriter();
            template.execute(writer, view);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
