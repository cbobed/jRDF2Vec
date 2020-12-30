package de.uni_mannheim.informatik.dws.jrdf2vec.walk_generators.data_structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * For reasons of performance, deletions are not possible currently.
 */
public class TripleDataSetMemory {

    /**
     * Constructor
     */
    public TripleDataSetMemory(){
        subjectToTriple = new HashMap<>();
        predicateToTriple = new HashMap<>();
        objectToTriple = new HashMap<>();
        triples = new HashSet<>(); // disabled for reasons of performance
    }

    HashMap<String, ArrayList<Triple>> subjectToTriple;
    HashMap<String, ArrayList<Triple>> predicateToTriple;
    HashMap<String, ArrayList<Triple>> objectToTriple;
    HashSet<Triple> triples;
    private long size = 0;

    /**
     * Add the given triple as specified by its components.
     * @param subject Subject
     * @param predicate Predicate
     * @param object Object
     */
    public void add(String subject, String predicate, String object){
        add(new Triple(subject, predicate, object));
    }

    /**
     * Add the given triple.
     * @param tripleToAdd Triple to be added.
     */
    public synchronized void add(Triple tripleToAdd){
        if(this.triples.contains(tripleToAdd)){
            return;
        }
        ArrayList<Triple> subjectToTripleList = subjectToTriple.get(tripleToAdd.subject);
        if(subjectToTripleList == null){
            ArrayList<Triple> newList = new ArrayList<>();
            newList.add(tripleToAdd);
            subjectToTriple.put(tripleToAdd.subject, newList);
        } else subjectToTripleList.add(tripleToAdd);

        ArrayList<Triple> predicateToTripleList = predicateToTriple.get(tripleToAdd.predicate);
        if(predicateToTripleList == null){
            ArrayList<Triple> newList = new ArrayList<>();
            newList.add(tripleToAdd);
            predicateToTriple.put(tripleToAdd.predicate, newList);
        } else predicateToTripleList.add(tripleToAdd);

        ArrayList<Triple> objectToTripleList = objectToTriple.get(tripleToAdd.object);
        if(objectToTripleList == null){
            ArrayList<Triple> newList = new ArrayList<>();
            newList.add(tripleToAdd);
            objectToTriple.put(tripleToAdd.object, newList);
        } else objectToTripleList.add(tripleToAdd);
        triples.add(tripleToAdd);
        size++;
    }

    /**
     * Adds all triples of {@code dataToAdd} to this triple set.
     * @param dataToAdd The data that shall be added to this triple set
     */
    public synchronized void addAll(TripleDataSetMemory dataToAdd){
        for(Triple triple : dataToAdd.triples){
            this.add(triple);
        }
    }

    public HashSet<Triple> getAllTriples(){
        return this.triples;
    }

    public ArrayList<Triple> getTriplesInvolvingSubject(String subject){
        return subjectToTriple.get(subject);
    }

    public ArrayList<Triple> getTriplesInvolvingPredicate(String predicate){
        return predicateToTriple.get(predicate);
    }

    public ArrayList<Triple> getTriplesInvolvingObject(String object){
        return objectToTriple.get(object);
    }

    /**
     * Returns the number of managed triples.
     * @return The number of managed triples.
     */
    public long getSize(){
        return size;
    }

    /**
     * Obtain a set of all subjects.
     * @return Subject set.
     */
    public Set<String> getUniqueSubjects(){
        return subjectToTriple.keySet();
    }

    public Set<String> getUniqueSubjectsAndObjects(){
        HashSet<String> result = new HashSet<>(subjectToTriple.keySet().size() + objectToTriple.keySet().size());
        result.addAll(subjectToTriple.keySet());
        result.addAll(objectToTriple.keySet());
        return result;
    }

}
