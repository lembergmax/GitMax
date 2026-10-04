package de.lembergmax.gitmax.ui.viewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

import com.google.android.material.color.MaterialColors;

import de.lembergmax.gitmax.domain.syntax.SyntaxToken;

import java.util.List;

/**
 * Ein Textfeld für Code: zeigt Zeilennummern in einem Rand links an. Bei Zeilenumbruch trägt nur die
 * erste Bildschirmzeile einer Zeile die Nummer. Dasselbe Feld dient als Viewer (schreibgeschützt,
 * auswählbar) und als Editor.
 */
public final class CodeEditText extends AppCompatEditText {

    private static final float GUTTER_PADDING_DP = 8f;
    private static final float NUMBER_TEXT_SP = 12f;
    private static final int MIN_DIGITS = 2;

    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect clip = new Rect();
    private final int basePaddingLeft;
    private final float gutterPadding;
    private int gutterWidth;
    private int shownDigits;
    private boolean lineNumbers = true;

    public CodeEditText(
            @NonNull final Context context
    ) {
        this(context, null);
    }

    public CodeEditText(
            @NonNull final Context context,
            @Nullable final AttributeSet attrs
    ) {
        super(context, attrs);
        // Der Text gehört nicht in den Instanzzustand der Activity: Bei großen Dateien sprengt er die Binder-Grenze, und
        // ungespeicherte Änderungen hält das ViewModel des Viewers (FileViewerViewModel.keepDraft) über eine Drehung hinweg.
        setFreezesText(false);
        basePaddingLeft = getPaddingLeft();
        gutterPadding = GUTTER_PADDING_DP * getResources().getDisplayMetrics().density;
        numberPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, NUMBER_TEXT_SP, getResources().getDisplayMetrics()));
        numberPaint.setTextAlign(Paint.Align.RIGHT);
        numberPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
        numberPaint.setTypeface(getTypeface() == null ? Typeface.MONOSPACE : getTypeface());
        updateGutter();
    }

    /** Farbspan der Syntaxfärbung; eigene Klasse, damit sich nur sie gezielt entfernen lässt. */
    private static final class SyntaxSpan extends ForegroundColorSpan {

        private SyntaxSpan(
                final int color
        ) {
            super(color);
        }
    }

    /**
     * Ersetzt die Syntaxfärbung durch die gegebenen Abschnitte.
     *
     * @param colors Farbe je {@link SyntaxToken.Type}, in der Reihenfolge von {@link SyntaxToken.Type#values()}
     */
    public void applySyntax(
            @NonNull final List<SyntaxToken> tokens,
            @NonNull final int[] colors
    ) {
        final Editable text = getText();
        if (text == null) {
            return;
        }
        for (final SyntaxSpan span : text.getSpans(0, text.length(), SyntaxSpan.class)) {
            text.removeSpan(span);
        }
        for (final SyntaxToken token : tokens) {
            if (token.end() <= text.length()) {
                text.setSpan(new SyntaxSpan(colors[token.type().ordinal()]), token.start(), token.end(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    /** Schaltet die Zeilennummern ein oder aus. */
    public void setLineNumbers(
            final boolean enabled
    ) {
        lineNumbers = enabled;
        updateGutter();
        invalidate();
    }

    @Override
    protected void onTextChanged(
            final CharSequence text,
            final int start,
            final int lengthBefore,
            final int lengthAfter
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (numberPaint != null) {
            updateGutter();
        }
    }

    @Override
    protected void onDraw(
            @NonNull final Canvas canvas
    ) {
        super.onDraw(canvas);
        final Layout layout = getLayout();
        if (!lineNumbers || layout == null || !canvas.getClipBounds(clip)) {
            return;
        }
        final CharSequence text = getText();
        final int firstLine = layout.getLineForVertical(Math.max(0, clip.top - getTotalPaddingTop()));
        final int lastLine = layout.getLineForVertical(Math.max(0, clip.bottom - getTotalPaddingTop()));
        final float right = gutterWidth - gutterPadding + getScrollX();
        int number = 1;
        for (int line = 0; line < firstLine; line += 1) {
            final int lineStart = layout.getLineStart(line + 1);
            if (lineStart > 0 && text.charAt(lineStart - 1) == '\n') {
                number += 1;
            }
        }
        for (int line = firstLine; line <= lastLine && line < layout.getLineCount(); line += 1) {
            final int start = layout.getLineStart(line);
            if (start == 0 || text.charAt(start - 1) == '\n') {
                final float baseline = layout.getLineBaseline(line) + getTotalPaddingTop();
                canvas.drawText(String.valueOf(number), right, baseline, numberPaint);
                number += 1;
            }
        }
    }

    private void updateGutter() {
        final int digits = lineNumbers ? Math.max(MIN_DIGITS, String.valueOf(Math.max(1, countLines())).length()) : 0;
        if (digits == shownDigits && gutterWidth == computeWidth(digits)) {
            return;
        }
        shownDigits = digits;
        gutterWidth = computeWidth(digits);
        setPadding(basePaddingLeft + gutterWidth, getPaddingTop(), getPaddingRight(), getPaddingBottom());
    }

    private int computeWidth(
            final int digits
    ) {
        if (digits == 0) {
            return 0;
        }
        return Math.round(numberPaint.measureText("0") * digits + gutterPadding * 2);
    }

    private int countLines() {
        final CharSequence text = getText();
        if (text == null) {
            return 1;
        }
        int lines = 1;
        for (int index = 0; index < text.length(); index += 1) {
            if (text.charAt(index) == '\n') {
                lines += 1;
            }
        }
        return lines;
    }
}
