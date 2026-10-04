package de.lembergmax.gitmax.ui.viewer;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;

import de.lembergmax.gitmax.domain.syntax.SyntaxHighlighter;
import de.lembergmax.gitmax.domain.syntax.SyntaxLanguage;
import de.lembergmax.gitmax.domain.syntax.SyntaxToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Hält die Syntaxfärbung eines Code-Felds aktuell, ohne die Oberfläche zu blockieren: Die Abschnitte der ganzen Datei
 * entstehen im Hintergrund; als Spans bekommt das Feld nur die Zeilen im und um das sichtbare Fenster, denn zehntausende
 * Spans auf einmal lassen den Hauptthread minutenlang stehen. Beim Scrollen wandert das Fenster mit, nach einer Änderung
 * wird neu berechnet.
 */
final class SyntaxController {

    private static final long TEXT_DELAY_MS = 300L;
    private static final long SCROLL_DELAY_MS = 60L;
    /** Zeilen über und unter dem sichtbaren Bereich, die mitgefärbt werden. */
    private static final int MARGIN_LINES = 60;
    /** Höchstzahl Spans je Fenster: Eine einzige riesige Zeile (minifiziertes JavaScript) darf nicht alles aufbieten. */
    private static final int MAX_SPANS = 3000;

    private final CodeEditText code;
    private final NestedScrollView scroll;
    private final Executor background;
    private final int[] colors;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable retokenize = this::startTokenizing;
    private final Runnable moveWindow = this::applyWindow;

    private SyntaxLanguage language = SyntaxLanguage.PLAIN;
    private List<SyntaxToken> tokens = List.of();
    private int tokenizedLength = -1;
    private int generation;
    private boolean stopped;

    SyntaxController(
            @NonNull final CodeEditText code,
            @NonNull final NestedScrollView scroll,
            @NonNull final Executor background,
            @NonNull final int[] colors
    ) {
        this.code = Objects.requireNonNull(code, "code");
        this.scroll = Objects.requireNonNull(scroll, "scroll");
        this.background = Objects.requireNonNull(background, "background");
        this.colors = Objects.requireNonNull(colors, "colors");
    }

    void setLanguage(
            @NonNull final SyntaxLanguage newLanguage
    ) {
        language = newLanguage;
    }

    /** Der Text wurde ersetzt (z. B. eine Datei geladen): sofort neu färben. */
    void textLoaded() {
        main.removeCallbacks(retokenize);
        startTokenizing();
    }

    /** Der Text wurde bearbeitet: kurz abwarten, dann neu färben. */
    void textChanged() {
        main.removeCallbacks(retokenize);
        main.postDelayed(retokenize, TEXT_DELAY_MS);
    }

    /** Der Nutzer hat gescrollt oder das Layout hat sich geändert: das Fenster der Spans nachziehen. */
    void viewChanged() {
        main.removeCallbacks(moveWindow);
        main.postDelayed(moveWindow, SCROLL_DELAY_MS);
    }

    /** Beendet alle ausstehende Arbeit, z. B. wenn die Ansicht zerstört wird. */
    void stop() {
        stopped = true;
        generation += 1;
        main.removeCallbacks(retokenize);
        main.removeCallbacks(moveWindow);
    }

    private void startTokenizing() {
        final Editable text = code.getText();
        if (stopped || text == null) {
            return;
        }
        generation += 1;
        final int current = generation;
        final SyntaxLanguage forLanguage = language;
        final String snapshot = text.toString();
        background.execute(() -> {
            final List<SyntaxToken> found = SyntaxHighlighter.highlight(snapshot, forLanguage);
            main.post(() -> {
                final Editable now = code.getText();
                if (stopped || current != generation || now == null || now.length() != snapshot.length()) {
                    return;
                }
                tokens = found;
                tokenizedLength = snapshot.length();
                applyWindow();
            });
        });
    }

    private void applyWindow() {
        final Editable text = code.getText();
        final Layout layout = code.getLayout();
        if (stopped || text == null || text.length() != tokenizedLength) {
            return;
        }
        if (layout == null || layout.getLineCount() == 0) {
            viewChanged();
            return;
        }
        final int top = scroll.getScrollY() - topInScroll();
        final int bottom = top + scroll.getHeight();
        final int firstLine = Math.max(0, layout.getLineForVertical(Math.max(0, top)) - MARGIN_LINES);
        final int lastLine = Math.min(layout.getLineCount() - 1, layout.getLineForVertical(Math.max(0, bottom)) + MARGIN_LINES);
        final int from = layout.getLineStart(firstLine);
        final int to = layout.getLineEnd(lastLine);
        code.applySyntax(window(from, to), colors);
    }

    /** Die Abschnitte, die das Fenster {@code [from, to)} berühren, höchstens {@link #MAX_SPANS}. */
    private List<SyntaxToken> window(
            final int from,
            final int to
    ) {
        int low = 0;
        int high = tokens.size();
        while (low < high) {
            final int middle = (low + high) >>> 1;
            if (tokens.get(middle).end() <= from) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        final List<SyntaxToken> visible = new ArrayList<>();
        for (int index = low; index < tokens.size() && visible.size() < MAX_SPANS; index += 1) {
            final SyntaxToken token = tokens.get(index);
            if (token.start() >= to) {
                break;
            }
            visible.add(token);
        }
        return visible;
    }

    /** Abstand der Oberkante des Code-Felds zur Oberkante des Scroll-Inhalts. */
    private int topInScroll() {
        int offset = 0;
        View view = code;
        while (view != scroll) {
            offset += view.getTop();
            final ViewParent parent = view.getParent();
            if (!(parent instanceof View parentView) || parentView == scroll) {
                break;
            }
            view = parentView;
        }
        return offset;
    }
}
