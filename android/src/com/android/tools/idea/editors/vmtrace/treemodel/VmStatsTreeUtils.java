package com.android.tools.idea.editors.vmtrace.treemodel;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.treeStructure.treetable.TreeTable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.Locale;

public class VmStatsTreeUtils {
  public static void adjustTableColumnWidths(TreeTable table) {
    TableColumnModel columnModel = table.getColumnModel();
    FontMetrics fm = table.getFontMetrics(table.getFont());

    for (int i = 0; i < table.getColumnCount(); i++) {
      TableColumn column = columnModel.getColumn(i);
      column.setPreferredWidth(fm.stringWidth(getSampleTextForColumn(i)));
    }
  }

  private static String getSampleTextForColumn(int index) {
    return StatsTableColumn.fromColumnIndex(index).getSampleText();
  }

  public static void setCellRenderers(TreeTable table) {
    TableCellRenderer headerRenderer = new TableHeaderCellRenderer(table);
    for (StatsTableColumn c: StatsTableColumn.values()) {
      getTableColumn(table, c).setHeaderRenderer(headerRenderer);
    }

    TableCellRenderer renderer = new ProfileTimeRenderer();
    getTableColumn(table, StatsTableColumn.INVOCATION_COUNT).setCellRenderer(renderer);
    getTableColumn(table, StatsTableColumn.INCLUSIVE_TIME).setCellRenderer(renderer);
    getTableColumn(table, StatsTableColumn.EXCLUSIVE_TIME).setCellRenderer(renderer);
  }

  private static TableColumn getTableColumn(TreeTable table, StatsTableColumn column) {
    return table.getColumnModel().getColumn(column.getColumnIndex());
  }

  public static void setSpeedSearch(TreeTable treeTable) {
    new TableSpeedSearch(treeTable) {
      @Override
      protected boolean isMatchingElement(Object element, String pattern) {
        String text = super.getElementText(element);
        // match search as long as some portion of the text matches the pattern
        return text != null && text.contains(pattern);
      }
    };
  }

  // Enable sorting in the vm stats tree table.
  // Sorting is ill-defined in the context of a TreeTable since you can't sort across different levels in the
  // hierarchy. As a result, we can't hook into JTable's sorting infrastructure and we roll our own. This
  // implementation listens to mouse click events on the tree table's header, interprets them as sort requests
  // and passes them on to the model.
  public static void enableSorting(final TreeTable treeTable, final VmStatsTreeTableModel vmStatsTreeTableModel) {
    JTableHeader header = treeTable.getTableHeader();
    header.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        TableColumnModel columnModel = treeTable.getColumnModel();
        int index = columnModel.getColumnIndexAtX(e.getX());
        vmStatsTreeTableModel.sortByColumn(StatsTableColumn.fromColumnIndex(index));
      }
    });
  }

  /** A {@link TableCellRenderer} used to render certain columns of the VM Trace statistics tree table. */
  private static class ProfileTimeRenderer extends ColoredTableCellRenderer {
    private static final char FIGURE_SPACE_CHAR = '\u2007'; // \u2007 = figure space, space the width of a digit

    private final NumberFormat myNumberFormat;
    private final NumberFormat myPercentFormat;

    private boolean myFontCanDisplayFigureSpace;
    private double mySpacesPerFigureSpaceChar = -1;

    public ProfileTimeRenderer() {
      myPercentFormat = NumberFormat.getPercentInstance(Locale.getDefault());
      myPercentFormat.setMaximumFractionDigits(1);
      myPercentFormat.setMinimumFractionDigits(1);

      myNumberFormat = NumberFormat.getNumberInstance();
    }

    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setTextAlign(SwingConstants.RIGHT);

      if (value instanceof Long) {
        // Format long values as a number
        append(myNumberFormat.format(value));
      } else if (value instanceof Pair) {
        /** Interpret a pair of <Long,Double> as the time value and a percentage as generated by
         * {@link AbstractProfileDataNode#getValueAndPercentagePair}, and render them appropriately. */
        //noinspection unchecked
        Pair<Long,Double> p = (Pair<Long,Double>) value;
        append(myNumberFormat.format(p.getFirst()));
        append(" ");
        append(formatPercentage(p.getSecond()), SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }

    private String formatPercentage(Double d) {
      String format = myPercentFormat.format(d);
      return format.length() < 6 ? prefixWithSpaces(format, 6 - format.length()) : format; // 6 = "100.0%".length
    }

    private String prefixWithSpaces(String s, int nSpaces) {
      if (mySpacesPerFigureSpaceChar < 0) { // not initialized yet
        mySpacesPerFigureSpaceChar = 1;

        myFontCanDisplayFigureSpace = getFont().canDisplay(FIGURE_SPACE_CHAR);
        if (!myFontCanDisplayFigureSpace) {
          FontMetrics fm = getFontMetrics(getFont());
          mySpacesPerFigureSpaceChar = fm.charWidth(FIGURE_SPACE_CHAR) / fm.charWidth(' ');
        }
      }

      StringBuilder sb = new StringBuilder();
      if (myFontCanDisplayFigureSpace) {
        StringUtil.repeatSymbol(sb, FIGURE_SPACE_CHAR, nSpaces);
      }
      else {
        // When the current font cannot display the figure space character, we attempt to
        // use an appropriate number of regular spaces
        StringUtil.repeatSymbol(sb, ' ', (int)(nSpaces * mySpacesPerFigureSpaceChar) + 1);
      }

      sb.append(s);
      return sb.toString();
    }
  }

  /** A renderer that center aligns column titles. */
  private static class TableHeaderCellRenderer implements TableCellRenderer {
    private final TableCellRenderer myDefaultRenderer;

    public TableHeaderCellRenderer(TreeTable table) {
      myDefaultRenderer = table.getTableHeader().getDefaultRenderer();
      if (myDefaultRenderer instanceof DefaultTableCellRenderer) {
        ((DefaultTableCellRenderer)myDefaultRenderer).setHorizontalAlignment(SwingConstants.CENTER);
      }
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      return myDefaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
  }
}
