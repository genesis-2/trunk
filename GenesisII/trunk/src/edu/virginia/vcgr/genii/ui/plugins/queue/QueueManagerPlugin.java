package edu.virginia.vcgr.genii.ui.plugins.queue;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Collection;

import javax.swing.JFrame;

import org.morgan.utils.gui.GUIUtils;

import edu.virginia.vcgr.genii.ui.plugins.AbstractCombinedUIMenusPlugin;
import edu.virginia.vcgr.genii.ui.plugins.EndpointDescription;
import edu.virginia.vcgr.genii.ui.plugins.MenuType;
import edu.virginia.vcgr.genii.ui.plugins.UIPluginContext;
import edu.virginia.vcgr.genii.ui.plugins.UIPluginException;

public class QueueManagerPlugin extends AbstractCombinedUIMenusPlugin
{
	@Override
	protected void performMenuAction(UIPluginContext context, MenuType menuType)
			throws UIPluginException
	{
		try
		{
			JFrame frame = new JFrame("Queue Manager");
			
			QueueManagerPanel qPanel = new QueueManagerPanel(context);
			
			Container container = frame.getContentPane();
			container.setLayout(new GridBagLayout());
			
			container.add(qPanel, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0,
				GridBagConstraints.CENTER, GridBagConstraints.BOTH,
				new Insets(5, 5, 5, 5), 5, 5));
			
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			frame.pack();
			GUIUtils.centerWindow(frame);
			frame.setVisible(true);
			frame.toFront();
		}
		catch (Throwable cause)
		{
			if (cause instanceof UIPluginException)
				throw (UIPluginException)cause;
			else if (cause instanceof RuntimeException)
				throw (RuntimeException)cause;
			else
				throw new UIPluginException("Unable to create QueueManager.",
					cause);
		}
	}

	@Override
	final public boolean isEnabled(
		Collection<EndpointDescription> selectedDescriptions)
	{
		if (selectedDescriptions == null || selectedDescriptions.size() != 1)
			return false;
		
		return selectedDescriptions.iterator().next().typeInformation().isQueue();
	}
}