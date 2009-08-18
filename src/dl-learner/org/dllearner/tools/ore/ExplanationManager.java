package org.dllearner.tools.ore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dllearner.tools.ore.explanation.CachedExplanationGenerator;
import org.dllearner.tools.ore.explanation.RootFinder;
import org.dllearner.tools.ore.explanation.laconic.LaconicExplanationGenerator;
import org.mindswap.pellet.owlapi.PelletReasonerFactory;
import org.mindswap.pellet.owlapi.Reasoner;
import org.semanticweb.owl.model.OWLAxiom;
import org.semanticweb.owl.model.OWLClass;
import org.semanticweb.owl.model.OWLDataFactory;
import org.semanticweb.owl.model.OWLException;
import org.semanticweb.owl.model.OWLOntology;
import org.semanticweb.owl.model.OWLOntologyChange;
import org.semanticweb.owl.model.OWLOntologyChangeListener;
import org.semanticweb.owl.model.OWLOntologyManager;
import org.semanticweb.owl.model.OWLSubClassAxiom;

import uk.ac.manchester.cs.bhig.util.Tree;
import uk.ac.manchester.cs.owl.explanation.ordering.DefaultExplanationOrderer;
import uk.ac.manchester.cs.owl.explanation.ordering.ExplanationTree;

import com.clarkparsia.explanation.PelletExplanation;

public class ExplanationManager implements OWLOntologyChangeListener, RepairManagerListener{

	private static ExplanationManager instance;
	
	private OWLOntologyManager manager;
	private OWLDataFactory dataFactory;
	private PelletReasonerFactory reasonerFactory;
	private OWLOntology ontology;
	private Reasoner reasoner;
	private PelletExplanation regularExpGen;
	private LaconicExplanationGenerator laconicExpGen;
	private RootFinder rootFinder;
	private Map<OWLClass, Set<Set<OWLAxiom>>> regularExplanationCache;
	private Map<OWLClass, Set<Set<OWLAxiom>>> laconicExplanationCache;
	private Set<OWLClass> unsatClasses;
	private Set<OWLClass> rootClasses;
	boolean ontologyChanged = true;
	boolean isLaconicMode = false;
	private boolean isComputeAllExplanations = false;
	private int maxExplantionCount = 1;
	private List<ExplanationManagerListener> listeners;
	
	private boolean allExplanationWarningChecked = false;
	
	private CachedExplanationGenerator gen;
	
	
	private ExplanationManager(Reasoner reasoner) {
		regularExplanationCache = new HashMap<OWLClass, Set<Set<OWLAxiom>>>();
		laconicExplanationCache = new HashMap<OWLClass, Set<Set<OWLAxiom>>>();

		this.reasoner = reasoner;
		this.manager = reasoner.getManager();
		this.ontology = reasoner.getLoadedOntologies().iterator().next();
		
		manager.addOntologyChangeListener(this);
//		manager.addOntologyChangeListener(reasoner);
		dataFactory = manager.getOWLDataFactory();
		RepairManager.getRepairManager(reasoner).addListener(this);
		reasonerFactory = new PelletReasonerFactory();

		rootFinder = new RootFinder(manager, reasoner, reasonerFactory);

		regularExpGen = new PelletExplanation(reasoner.getManager(), reasoner.getLoadedOntologies());
	
		laconicExpGen = new LaconicExplanationGenerator(manager,
				reasonerFactory, manager.getOntologies());

		rootClasses = new HashSet<OWLClass>();
		unsatClasses = new HashSet<OWLClass>();
		
		listeners = new ArrayList<ExplanationManagerListener>();
		
		gen = new CachedExplanationGenerator(ontology);

	}
	
	public static synchronized ExplanationManager getExplanationManager(
			Reasoner reasoner) {
		if (instance == null) {
			instance = new ExplanationManager(reasoner);
		}
		return instance;
	}
	
	public static synchronized ExplanationManager getExplanationManager(){
	
		return instance;
	}
	
	public Set<OWLClass> getUnsatisfiableClasses(){
		computeRootUnsatisfiableClasses();
		return unsatClasses;
	}
	
	public Set<OWLClass> getRootUnsatisfiableClasses(){
		computeRootUnsatisfiableClasses();
		return rootClasses;
	}
	
		
	private void computeRootUnsatisfiableClasses(){
		if(ontologyChanged){
			rootClasses.clear();
			unsatClasses.clear();
			unsatClasses.addAll(reasoner.getInconsistentClasses());
			rootClasses.addAll(rootFinder.getRootClasses());
			ontologyChanged = false;
		}
		
	}
	
	public Set<List<OWLAxiom>> getUnsatisfiableExplanations(OWLClass unsat) {

		OWLSubClassAxiom entailment = dataFactory.getOWLSubClassAxiom(unsat,
				dataFactory.getOWLNothing());

		Set<Set<OWLAxiom>> explanations;
		if (isComputeAllExplanations) {
			explanations = gen.getExplanations(entailment);
		} else {
			explanations = gen.getExplanations(entailment, maxExplantionCount);
		}

		return getOrderedExplanations(entailment, explanations);
	}
	
	public Set<List<OWLAxiom>> getInconsistencyExplanations(){
		OWLSubClassAxiom entailment = dataFactory.getOWLSubClassAxiom(dataFactory.getOWLThing(),
				dataFactory.getOWLNothing());

		Set<Set<OWLAxiom>> explanations;
		if (isComputeAllExplanations) {
			explanations = gen.getExplanations(entailment);
		} else {
			explanations = gen.getExplanations(entailment, maxExplantionCount);
		}

		return getOrderedExplanations(entailment, explanations);
	}
	
	
	
	private ArrayList<OWLAxiom> getTree2List(Tree<OWLAxiom> tree){
		ArrayList<OWLAxiom> ordering = new ArrayList<OWLAxiom>();
		ordering.add((OWLAxiom)tree.getUserObject());
		for(Tree<OWLAxiom> child : tree.getChildren()){
			ordering.addAll(getTree2List(child));
		}
		return ordering;
	}
	
	private Set<List<OWLAxiom>> getOrderedExplanations(OWLAxiom entailment, Set<Set<OWLAxiom>> explanations){
		DefaultExplanationOrderer orderer = new DefaultExplanationOrderer();
		Set<List<OWLAxiom>> orderedExplanations = new HashSet<List<OWLAxiom>>();
		ArrayList<OWLAxiom> ordering;
		for(Set<OWLAxiom> explanation : explanations){
			ordering = new ArrayList<OWLAxiom>();
			ExplanationTree tree = orderer.getOrderedExplanation(entailment, explanation);
			
//			ordering.add(tree.getUserObject());
			for(Tree<OWLAxiom> child : tree.getChildren()){
				ordering.addAll(getTree2List(child));
			}
			orderedExplanations.add(ordering);
		}
		return orderedExplanations;
	}

	@Override
	public void ontologiesChanged(List<? extends OWLOntologyChange> changes)
			throws OWLException {
		ontologyChanged = true;	
	}
	
	public int getArity(OWLClass cl, OWLAxiom ax) {
		int arity = 0;
		
		Set<Set<OWLAxiom>> explanations = gen.getExplanations(dataFactory.getOWLSubClassAxiom(cl, dataFactory.getOWLNothing()));
		
		if(explanations != null){
			
			for (Set<OWLAxiom> explanation : explanations) {
				if (explanation.contains(ax)) {
					arity++;
				}
			}
		}
		return arity;
	}
	
	public void setLaconicMode(boolean laconic){
		gen.setComputeLaconicExplanations(laconic);
		fireExplanationLimitChanged();
		
	}
	
	public void setComputeAllExplanationsMode(boolean value){
		isComputeAllExplanations = value;
		fireExplanationLimitChanged();
	}

	public boolean isComputeAllExplanationsMode(){
		return isComputeAllExplanations;
	}
	
	public void setMaxExplantionCount(int limit){
		maxExplantionCount = limit;
		fireExplanationLimitChanged();
	}
	
	public int getMaxExplantionCount(){
		return maxExplantionCount;
	}

	@Override
	public void repairPlanExecuted() {
		reasoner.refresh();
		ontologyChanged = true;
		regularExpGen = new PelletExplanation(reasoner.getManager(), reasoner.getLoadedOntologies());
		laconicExpGen = new LaconicExplanationGenerator(manager,
				reasonerFactory, reasoner.getLoadedOntologies());
		regularExplanationCache.clear();
		laconicExplanationCache.clear();
	}

	@Override
	public void repairPlanChanged() {
		// TODO Auto-generated method stub
		
	}
	
	public void addListener(ExplanationManagerListener l){
		listeners.add(l);
	}
	
	public void removeListener(ExplanationManagerListener l){
		listeners.remove(l);
	}
	
	public void fireExplanationLimitChanged(){
		for(ExplanationManagerListener listener : listeners){
			listener.explanationLimitChanged();
		}
	}
	
	public void setAllExplanationWarningChecked(){
		allExplanationWarningChecked = true;
	}
	
	public boolean isAllExplanationWarningChecked(){
		return allExplanationWarningChecked;
	}
	
	
}
