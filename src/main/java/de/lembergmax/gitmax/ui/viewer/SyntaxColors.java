package de.lembergmax.gitmax.ui.viewer;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;

import de.lembergmax.gitmax.R;
import de.lembergmax.gitmax.domain.syntax.SyntaxToken;

/** Liest die Farben der Syntaxfärbung aus dem Theme, in der Reihenfolge von {@link SyntaxToken.Type#values()}. */
final class SyntaxColors {

    private SyntaxColors() {
    }

    @NonNull
    static int[] load(
            @NonNull final Context context
    ) {
        final SyntaxToken.Type[] types = SyntaxToken.Type.values();
        final int[] colors = new int[types.length];
        for (final SyntaxToken.Type type : types) {
            colors[type.ordinal()] = MaterialColors.getColor(context, attributeOf(type), 0);
        }
        return colors;
    }

    private static int attributeOf(
            final SyntaxToken.Type type
    ) {
        switch (type) {
            case KEYWORD:
                return R.attr.colorSyntaxKeyword;
            case STRING:
                return R.attr.colorSyntaxString;
            case COMMENT:
                return R.attr.colorSyntaxComment;
            case NUMBER:
                return R.attr.colorSyntaxNumber;
            case TAG:
                return R.attr.colorSyntaxTag;
            case ATTRIBUTE:
                return R.attr.colorSyntaxAttribute;
            case KEY:
                return R.attr.colorSyntaxKey;
            case LITERAL:
                return R.attr.colorSyntaxLiteral;
            case HEADING:
            default:
                return R.attr.colorSyntaxHeading;
        }
    }
}
