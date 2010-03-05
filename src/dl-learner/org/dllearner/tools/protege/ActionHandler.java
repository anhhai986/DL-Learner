/**
 * Copyright (C) 2007-2009, Jens Lehmann
 *
 * This file is part of DL-Learner.
 * 
 * DL-Learner is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * DL-Learner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.dllearner.tools.protege;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.core.EvaluatedDescription;
import org.dllearner.core.LearningAlgorithm;

/**
 * This class processes input from the user.
 * 
 * @author Christian Koetteritzsch
 * 
 */
public class ActionHandler implements ActionListener {

	// This is the DLLearnerModel.

	private final DLLearnerModel model;
	

	// This is the id that checks if the equivalent class or subclass button is
	// pressed in protege
	// this is a boolean that checked if the advanced button was pressed or not.
	private boolean toggled;
	// This is the Tread of the DL-Learner
	private EvaluatedDescription evaluatedDescription;
	// This is the view of the DL-Learner tab.
	private Timer timer;
	private LearningAlgorithm la;
	private SuggestionRetriever retriever;
	private HelpTextPanel helpPanel;
	private final Color colorRed = new Color(139, 0, 0);
	private final Color colorGreen = new Color(0, 139, 0);
	private final DLLearnerView view;
	private static final String HELP_BUTTON_STRING = "help";
	private static final String ADD_BUTTON_STRING = "<html>ADD</html>";
	private static final String ADVANCED_BUTTON_STRING = "Advanced";
	private static final String EQUIVALENT_CLASS_LEARNING_STRING = "<html>suggest equivalent class expression</html>";
	private static final String SUPER_CLASS_LEARNING_STRING = "<html>suggest super class expression</html>";
	private static JOptionPane optionPane;

	/**
	 * This is the constructor for the action handler.
	 * 
	 * @param m
	 *            DLLearnerModel
	 * @param view
	 *            DLlearner tab
	 * 
	 */
	public ActionHandler(DLLearnerModel m, DLLearnerView view) {
		this.view = view;
		this.model = m;
		toggled = false;
		helpPanel = new HelpTextPanel(view);
		optionPane = new JOptionPane();
		

	}

	/**
	 * When a Button is pressed this method select the right.
	 * 
	 * @param z
	 *            ActionEvent
	 */
	public void actionPerformed(ActionEvent z) {

		if (z.getActionCommand().equals(EQUIVALENT_CLASS_LEARNING_STRING)
				|| z.getActionCommand().equals(SUPER_CLASS_LEARNING_STRING)) {
			model.setKnowledgeSource();
			view.getSuggestClassPanel().getSuggestionsTable().clear();
			view.getSuggestClassPanel().repaint();
			model.setLearningProblem();
			model.setLearningAlgorithm();
			view.getRunButton().setEnabled(false);
			view.getHintPanel().setForeground(Color.RED);
			CELOE celoe = (CELOE) model.getLearningAlgorithm();

			String moreInformationsMessage = "<html><font size=\"3\">Learning started. Currently searching class expressions with length between "
					+ celoe.getMinimumHorizontalExpansion()
					+ " and "
					+ celoe.getMaximumHorizontalExpansion() + ".</font></html>";
			view.setHelpButtonVisible(true);
			view.setHintMessage(moreInformationsMessage);
			retriever = new SuggestionRetriever();
			retriever.addPropertyChangeListener(view.getStatusBar());
			retriever.execute();
		}

		if (z.getActionCommand().equals(ADD_BUTTON_STRING)) {
			model.changeDLLearnerDescriptionsToOWLDescriptions(evaluatedDescription.getDescription());
			String message = "<html><font size=\"3\">class expression added</font></html>";
			view.setHintMessage(message);
			view.setHelpButtonVisible(false);
		}
		if (z.toString().contains(ADVANCED_BUTTON_STRING)) {
			if (!toggled) {
				toggled = true;
				view.setIconToggled(toggled);
				view.setExamplePanelVisible(toggled);
			} else {
				toggled = false;
				view.setIconToggled(toggled);
				view.setExamplePanelVisible(toggled);
			}
		}
		if (z.toString().contains(HELP_BUTTON_STRING)) {

			Set<String> uris = model.getOntologyURIString();
			String currentClass = "";
			for (String uri : uris) {
				if (model.getCurrentConcept().toString().contains(uri)) {
					currentClass = model.getCurrentConcept()
							.toManchesterSyntaxString(uri, null);
				}
			}
			
			//helpPanel.renderHelpTextMessage(currentClass);
			//view.getLearnerView().add();
			//help = new JTextPane();
			//help.setText(helpText);
			optionPane.setPreferredSize(new Dimension(300, 200));
			JOptionPane.showMessageDialog(view.getLearnerView(), helpPanel.renderHelpTextMessage(currentClass), "Help",
					JOptionPane.INFORMATION_MESSAGE);
		}
	}

	/**
	 * Resets the toggled Button after the plugin is closed.
	 */
	public void resetToggled() {
		toggled = false;
	}

	/**
	 * This Methode sets the evaluated class expression that is selected in the
	 * panel.
	 * 
	 * @param desc
	 *            evaluated descriptions
	 */
	public void setEvaluatedClassExpression(EvaluatedDescription desc) {
		this.evaluatedDescription = desc;
	}

	/**
	 * Inner Class that retrieves the concepts given by the DL-Learner.
	 * 
	 * @author Christian Koetteritzsch
	 * 
	 */
	class SuggestionRetriever
			extends
			SwingWorker<List<? extends EvaluatedDescription>, List<? extends EvaluatedDescription>> {

		private Thread dlLearner;
		private final DefaultListModel dm = new DefaultListModel();
		private boolean isFinished; 

		@SuppressWarnings("unchecked")
		@Override
		protected List<? extends EvaluatedDescription> doInBackground()
				throws Exception {
			setProgress(0);
			la = model.getLearningAlgorithm();
			view.setStatusBarVisible(true);
			view.getStatusBar().setMaximumValue(
					view.getPosAndNegSelectPanel().getOptionPanel()
							.getMaxExecutionTime());
			timer = new Timer();
			isFinished = false;
			timer.schedule(new TimerTask() {
				int progress = 0;

				@Override
				public void run() {
					progress += 1;
					setProgress(progress);
					if(progress == view.getPosAndNegSelectPanel().getOptionPanel()
							.getMaxExecutionTime() - 1) {
						isFinished = true;
					}
					if (la != null) {
						publish(la.getCurrentlyBestEvaluatedDescriptions(view
								.getPosAndNegSelectPanel().getOptionPanel()
								.getNrOfConcepts()));
						CELOE celoe = (CELOE) model.getLearningAlgorithm();
						view.getHintPanel().setForeground(Color.RED);
						String moreInformationsMessage = "<html><font size=\"3\">Learning started. Currently searching class expressions with length between "
								+ celoe.getMinimumHorizontalExpansion()
								+ " and "
								+ celoe.getMaximumHorizontalExpansion()
								+ ".</font></html>";
						view.setHintMessage(moreInformationsMessage);
					}
				}

			}, 1000, 1000);

			dlLearner = new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						model.run();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

			});
			dlLearner.start();

			try {
				dlLearner.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			List<? extends EvaluatedDescription> result = la
					.getCurrentlyBestEvaluatedDescriptions(view
							.getPosAndNegSelectPanel().getOptionPanel()
							.getNrOfConcepts());

			return result;
		}

		@Override
		public void done() {

			timer.cancel();

			List<? extends EvaluatedDescription> result = null;
			try {
				result = get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			setProgress(0);
			view.stopStatusBar();
			updateList(result);
			view.algorithmTerminated();

		}

		@Override
		protected void process(
				List<List<? extends EvaluatedDescription>> resultLists) {

			for (List<? extends EvaluatedDescription> list : resultLists) {
				updateList(list);
			}
		}

		private void updateList(
				final List<? extends EvaluatedDescription> result) {

			Runnable doUpdateList = new Runnable() {

				public void run() {
					model.setSuggestList(result);
//					dm.clear();
//					int i = 0;
//					for (EvaluatedDescription eval : result) {
//						Set<String> ont = model.getOntologyURIString();
//						for (String ontology : ont) {
//							if (eval.getDescription().toString().contains(
//									ontology)) {
//								if (((EvaluatedDescriptionClass) eval)
//										.isConsistent()) {
//									dm.add(i, eval);
////									dm.add(i, new SuggestListItem(colorGreen,
////											eval.getDescription()
////													.toManchesterSyntaxString(
////															ontology, null),
////											((EvaluatedDescriptionClass) eval)
////													.getAccuracy() * 100));
//									i++;
//									break;
//								} else {
//									dm.add(i, eval);
////									dm.add(i, new SuggestListItem(colorRed,
////											eval.getDescription()
////													.toManchesterSyntaxString(
////															ontology, null),
////											((EvaluatedDescriptionClass) eval)
////													.getAccuracy() * 100));
//									if(isFinished) {
//										view.setIsInconsistent(true);
//									}
//									i++;
//									break;
//								}
//							}
//						}
//					}
//
//					view.getSuggestClassPanel().setSuggestList(dm);
//					view.getLearnerView().repaint();
					view.getSuggestClassPanel().addSuggestions(result);
				}
			};
			SwingUtilities.invokeLater(doUpdateList);

		}

	}

}
