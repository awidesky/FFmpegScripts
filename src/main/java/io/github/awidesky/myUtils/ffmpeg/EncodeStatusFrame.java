package io.github.awidesky.myUtils.ffmpeg;

import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

class CustomTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 5495422621043965010L;
    
	private final String[] columnNames = {"FileName", "Frame", "FPS", "Time", "Speed", "Status"};
    private final List<EncodeStatus> status = new ArrayList<>();

    public void addRow(EncodeStatus row) {
        status.add(row);
        fireTableRowsInserted(status.size() - 1, status.size() - 1);
    }

    public void updated(EncodeStatus row) {
        fireTableRowsUpdated(status.indexOf(row), status.indexOf(row));
    }

    public EncodeStatus getRow(int rowIndex) {
        return status.get(rowIndex);
    }

    @Override
    public int getRowCount() {
        return status.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        EncodeStatus row = status.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> row.getFile().getName();
            case 1 -> row.getFrame();
            case 2 -> row.getFps();
            case 3 -> row.getTime();
            case 4 -> row.getSpeed();
            case 5 -> row.getStatus();
            default -> null;
        };
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }
}

public class EncodeStatusFrame extends JFrame {
    private static final long serialVersionUID = 2014707731371717590L;

    private CustomTableModel model;
    
	public EncodeStatusFrame() {
        setTitle("Custom JTable with Editable Model");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 300);
        setLocationRelativeTo(null);

        model = new CustomTableModel();

        //model.addRow(new EncodeStatus("video2.mp4", 250, 24.00, 0.8, "Paused"));

        JTable table = new JTable(model) {

            private static final long serialVersionUID = 5299145840097237290L;

			@Override
            public String getToolTipText(MouseEvent e) {
                java.awt.Point p = e.getPoint();
                int rowIndex = rowAtPoint(p);
                int colIndex = columnAtPoint(p);

                if (rowIndex == -1 || colIndex == -1) {
                    return null;
                }

                Object value = getValueAt(rowIndex, colIndex);
                CustomTableModel model = (CustomTableModel) getModel();
                EncodeStatus row = model.getRow(rowIndex);

                // 예시: 컬럼별 툴팁 정의
                return switch (colIndex) {
                    case 0 -> row.getFile().getAbsolutePath();
                    case 1 -> row.getFrame();
                    case 2 -> row.getFps();
                    case 3 -> row.getTime();
                    case 4 -> row.getSpeed();
                    case 5 -> row.getStatus();
                    default -> value != null ? value.toString() : null;
                };
            }
        };
        JScrollPane scrollPane = new JScrollPane(table);

        getContentPane().add(scrollPane, BorderLayout.CENTER);
    }
	
	public EncodeStatus addTable(File outFile) {
		EncodeStatus e = new EncodeStatus(outFile, "", "", "", "", "Running");
		SwingUtilities.invokeLater(() -> model.addRow(e));
		return e;
	}
	
	public void updated(EncodeStatus e) {
		model.updated(e);
	}

}