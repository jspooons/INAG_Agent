package group1;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.EndNegotiation;
import genius.core.actions.Offer;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;

/**
 * 
 */
public class Agent1 extends AbstractNegotiationParty 
{
	private static double MINIMUM_TARGET = 0.8;
	private Bid lastOffer;
	private Bid beforeLastOffer;
	private Bid prevBid;
	
	// Array in hashmap below will be of form [freq, rank, value]
	//private Map<String, HashMap<String, ArrayList<Object>>> issues_freq_val = new HashMap<String, HashMap<String, ArrayList<Object>>>();
	private Map<String, LinkedHashMap<String, Integer>> freqs = new HashMap<String, LinkedHashMap<String, Integer>>();
	private Map<String, HashMap<String, Double>> vals = new HashMap<String, HashMap<String, Double>>();
	private Map<String, Double> weights = new HashMap<String, Double>();
	private int num_offers = 0;
	private List<Bid> samples = new ArrayList<>();
	
	/**
	 * Initializes a new instance of the agent.
	 */
	@Override
	public void init(NegotiationInfo info) 
	{	
		super.init(info);
		
		AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
		AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;
		
		List<Issue> issues = additiveUtilitySpace.getDomain().getIssues();
		
		for (Issue issue : issues) {
			int issueNumber = issue.getNumber();
			String issueName = issue.getName();
			System.out.println(">>   " + issueName + "   weight:   " + 
					additiveUtilitySpace.getWeight(issueNumber));
			
			// Assuming that issues are discrete only
			IssueDiscrete issueDiscrete = (IssueDiscrete) issue; //example of a discrete issue is colour of a car where values might be {Red, Black, Blue}
			EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issueNumber); //evaluator used to convert the value of a discrete issue to a utility
			
			weights.put(issueName, -1.0);
			freqs.put(issueName, new LinkedHashMap<String, Integer>());
			vals.put(issueName, new HashMap<String, Double>());
			
			for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
				String optionName = valueDiscrete.getValue();
				
				freqs.get(issueName).put(optionName, 0);
				vals.get(issueName).put(optionName, 0.0);
				
				System.out.println(optionName);
				System.out.println("Evaluation(getValue):   " + evaluatorDiscrete.getValue(valueDiscrete));
				
				try {
					System.out.println("Evaluation(getEvaluation):   " + evaluatorDiscrete.getValue(valueDiscrete));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * 
	 */
	@Override
	public Action chooseAction(List<Class<? extends Action>> possibleActions) 
	{
		// Check for acceptance if we have received an offer
		// Basically checking if a previous offer exists, if a previous offer exists, proceed with the
		// the code that exists in the if statement
		
		if (lastOffer != null) {
			
			num_offers += 1;
			List<Issue> issues = lastOffer.getIssues();
			
			updateFrequencies();
			updateRanks(issues);
			updateValuesAndWeights(issues);
			normaliseWeights(issues);
			
			System.out.println(freqs);
			System.out.println(vals);
			System.out.println(weights);
		
			if (num_offers > 50) {
				
				// Calculate offer that is best utility for opponent (using opponent model) and where 
				// current agents utility using their utility function is greater than the target.
				Map<Bid, Double> bidUtilities = calculateUtilities();
				System.out.println(bidUtilities);
				int size = bidUtilities.size();
				
				Iterator<Bid> sampleIterator = bidUtilities.keySet().iterator();
				Bid first = bidUtilities.keySet().iterator().next();
				
				for (int i=0; i<size; i++) {
					Bid sample = sampleIterator.next();
					if (getUtility(sample) >= MINIMUM_TARGET)
						return new Accept(getPartyId(), sample);
				}
				
				return new Accept(getPartyId(), first);
			}
			
			return new Offer(getPartyId(), generateRandomBidAboveTarget());
		}
		
		// Otherwise, send out a random offer above the target utility 
		// Sending out the first offer
		
		return new Offer(getPartyId(), getMaxUtilityBid());
	}
	
	private Map<Bid, Double> calculateUtilities() {
		Map<Bid, Double> bidUtilities = new HashMap<>();
		
		for (Bid sample : samples) {
			if (!bidUtilities.containsKey(sample))
				bidUtilities.put(sample, getOpponentUtility(sample));
		}
		
		// Sourced from https://howtodoinjava.com/java/sort/java-sort-map-by-values/
		LinkedHashMap<Bid, Double> sortedBidUtilities = new LinkedHashMap<>();
		bidUtilities.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())) 
			.forEachOrdered(x -> sortedBidUtilities.put(x.getKey(), x.getValue()));
		
		return sortedBidUtilities;
	}
	
	private double getOpponentUtility(Bid bid) {
		List<Issue> issues = bid.getIssues();
		double utility = 0.0;

		for (Issue issue : issues) {
			String issueName = issue.getName();
			int issueNo = issue.getNumber();
			
			String option = bid.getValues().get(issueNo).toString();
			utility += weights.get(issueName) * vals.get(issueName).get(option);
		}
		
		return utility;
	}
	
	private void updateFrequencies() {
		
		List<Issue> issues = lastOffer.getIssues();
		HashMap<Integer, Value> options = lastOffer.getValues();
		
		for (Issue issue : issues) {
			String issueName = issue.getName();
			int issueNo = issue.getNumber();
			
			String optionName = options.get(issueNo).toString();
			Integer count = freqs.get(issueName).get(optionName);
			
			freqs.get(issueName).put(optionName, count+1);
		}
	}
	
	// Sourced from https://howtodoinjava.com/java/sort/java-sort-map-by-values/
	private void updateRanks(List<Issue> issues) {
		for (Issue issue : issues) {
			LinkedHashMap<String, Integer> sorted = new LinkedHashMap<>();
			String issueName = issue.getName();
			freqs.get(issueName).entrySet()
				.stream()
				.sorted(Entry.comparingByValue(Comparator.reverseOrder()))
				.forEachOrdered(x -> sorted.put(x.getKey(), x.getValue()));
			
			freqs.replace(issueName, sorted);
		}
	}
	
	private void updateValuesAndWeights(List<Issue> issues) {
		
		for (Issue issue : issues) {
			String issueName = issue.getName();
			
			IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
			int k = issueDiscrete.getValues().size();
			Double weight = 0.0;
			
			int rank = 1;
			
			Iterator<String> optionIterator = freqs.get(issueName).keySet().iterator();
			for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
				//String optionName = valueDiscrete.getValue();
				
				Double value = (((double) k - (double) rank + 1.0)/(double) k);
				
				String optionName = optionIterator.next();
				
				vals.get(issueName).put(optionName, value);
				
				// Weight updates
				Integer freq = freqs.get(issueName).get(optionName);
				weight += (double) Math.pow((freq / (double) num_offers), 2);
				
				rank += 1;
			}
			
			weights.put(issueName, weight);
		}
	}
	
	private void normaliseWeights(List<Issue> issues) {
		Double normaliser = 0.0;
		for (Issue issue : issues) {
			String issueName = issue.getName();
			normaliser += weights.get(issueName);
		}
		
		for (Issue issue : issues) {
			String issueName = issue.getName();
			Double norm_weight = weights.get(issueName) / normaliser;
			weights.put(issueName, norm_weight);
		}
	}

	private Bid generateRandomBidAboveTarget() 
	{
		Bid randomBid;
		double util;
		int i = 0;
		// try 100 times to find a bid under the target utility
		do 
		{
			randomBid = generateRandomBid();
			util = utilitySpace.getUtility(randomBid);
		} 
		while (util < MINIMUM_TARGET && i++ < 100);		
		return randomBid;
	}

	/**
	 * Remembers the offers received by the opponent.
	 */
	@Override
	public void receiveMessage(AgentID sender, Action action) 
	{
		super.receiveMessage(sender, action);
		
		if (action instanceof Offer) // is offer of valid type?
		{
			if (lastOffer != null)
				beforeLastOffer = lastOffer;
				
			lastOffer = ((Offer) action).getBid();
			
			samples.add(lastOffer);
		}
	}

	@Override
	public String getDescription() 
	{
		return "Places random bids >= " + MINIMUM_TARGET;
	}

	/**
	 * This stub can be expanded to deal with preference uncertainty in a more sophisticated way than the default behaviour.
	 */
	@Override
	public AbstractUtilitySpace estimateUtilitySpace() 
	{
		return super.estimateUtilitySpace();
	}
	
	private Bid getMaxUtilityBid() {
		try {
			return utilitySpace.getMaxUtilityBid(); // highest possible utility (utility of the best possible offer)
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	private Bid getMinUtilityBid() {
		try {
			return utilitySpace.getMinUtilityBid(); // highest possible utility (utility of the best possible offer)
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}

}
