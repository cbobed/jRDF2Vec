package de.uni_mannheim.informatik.dws.jrdf2vec.walk_generators.base;

import de.uni_mannheim.informatik.dws.jrdf2vec.walk_generators.parsers.*;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.riot.Lang;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.uni_mannheim.informatik.dws.jrdf2vec.walk_generators.runnables.RandomWalkEntityProcessingRunnable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.zip.GZIPOutputStream;

import static de.uni_mannheim.informatik.dws.jrdf2vec.util.Util.readOntology;

/**
 * Default Walk Generator.
 * Intended to work on any data set.
 */
public class WalkGeneratorDefault extends WalkGenerator {

    /**
     * Default Logger.
     */
    Logger LOGGER = LoggerFactory.getLogger(WalkGeneratorDefault.class);

    /**
     * Inject default entity selector.
     */
    public EntitySelector entitySelector;

    /**
     * If not specified differently, this directory will be used to persist walks.
     */
    public final static String DEFAULT_WALK_DIRECTORY = "./walks";

    /**
     * If not specified differently, this file will be used to persists walks.
     */
    public final static String DEFAULT_WALK_FILE_TO_BE_WRITTEN = DEFAULT_WALK_DIRECTORY + "/walk_file.gz";

    /**
     * Can be set to false if there are problems with the parser to make sure that generation functions do not
     * start.
     */
    private boolean parserIsOk = true;


    /**
     * Constructor
     *
     * @param ontModel Model for which walks shall be generated.
     */
    public WalkGeneratorDefault(OntModel ontModel) {
        this.parser = new JenaOntModelMemoryParser();
        ((JenaOntModelMemoryParser) this.parser).readDataFromOntModel(ontModel);
        this.entitySelector = new MemoryEntitySelector(((JenaOntModelMemoryParser) parser).getData());
    }


    /**
     * Constructor
     *
     * @param tripleFile File to the NT file or, alternatively, to a directory of NT files.
     */
    public WalkGeneratorDefault(File tripleFile) {
        if (!tripleFile.exists()) {
            LOGGER.error("The resource file you specified does not exist. ABORT.");
            return;
        }
        if (tripleFile.isDirectory()) {
            LOGGER.warn("You specified a directory. Trying to parse files in the directory. The program will fail (later) " +
                    "if you use an entity selector that requires one ontology.");
            this.parser = new NtMemoryParser();
            ((NtMemoryParser) this.parser).readNtTriplesFromDirectoryMultiThreaded(tripleFile, false);
            this.entitySelector = new MemoryEntitySelector(((NtMemoryParser) this.parser).getData());
        } else {
            // decide on parser depending on file ending
            Pair<IParser, EntitySelector> parserSelectorPair = ParserManager.parseSingleFile(tripleFile);
            this.parser = parserSelectorPair.getValue0();
            this.entitySelector = parserSelectorPair.getValue1();
        }
    }


    /**
     * Constructor
     *
     * @param pathToTripleFile The path to the NT file.
     */
    public WalkGeneratorDefault(String pathToTripleFile) {
        this(new File(pathToTripleFile));
    }


    @Override
    public void generateWalks(WalkGenerationMode generationMode, int numberOfThreads, int numberOfWalks, int depth, String walkFile) {
        if (generationMode == null) {
            System.out.println("walkGeneration mode is null... Using default: RANDOM_WALKS_DUPLICATE_FREE");
            this.generateRandomWalksDuplicateFree(numberOfThreads, numberOfWalks, depth, walkFile);
        } else if (generationMode == WalkGenerationMode.MID_WALKS) {
            System.out.println("generate random mid walks...");
            this.generateRandomMidWalks(numberOfThreads, numberOfWalks, depth, walkFile);
        } else if (generationMode == WalkGenerationMode.MID_WALKS_DUPLICATE_FREE) {
            System.out.println("generate random mid walks duplicate free...");
            this.generateRandomMidWalksDuplicateFree(numberOfThreads, numberOfWalks, depth, walkFile);
        } else if (generationMode == WalkGenerationMode.RANDOM_WALKS) {
            System.out.println("generate random walks...");
            this.generateRandomWalks(numberOfThreads, numberOfWalks, depth, walkFile);
        } else if (generationMode == WalkGenerationMode.RANDOM_WALKS_DUPLICATE_FREE) {
            System.out.println("generate random walks duplicate free...");
            this.generateRandomWalksDuplicateFree(numberOfThreads, numberOfWalks, depth, walkFile);
        } else if (generationMode == WalkGenerationMode.MID_WALKS_WEIGHTED) {
            System.out.println("generate weighted mid walks...");
            this.generateWeightedMidWalks(numberOfThreads, numberOfWalks, depth, walkFile);
        } else {
            System.out.println("ERROR. Cannot identify the walkGenenerationMode chosen. Aborting program.");
        }
    }

    @Override
    public void generateRandomWalks(int numberOfThreads, int numberOfWalksPerEntity, int depth) {
        generateRandomWalks(numberOfThreads, numberOfWalksPerEntity, depth, DEFAULT_WALK_FILE_TO_BE_WRITTEN);
    }

    @Override
    public void generateRandomWalks(int numberOfThreads, int numberOfWalksPerEntity, int depth, String filePathOfFileToBeWritten) {
        this.filePath = filePathOfFileToBeWritten;
        generateRandomWalksForEntities(entitySelector.getEntities(), numberOfThreads, numberOfWalksPerEntity, depth);
    }

    @Override
    public void generateRandomWalksDuplicateFree(int numberOfThreads, int numberOfWalksPerEntity, int depth, String filePathOfFileToBeWritten) {
        this.filePath = filePathOfFileToBeWritten;
        generateDuplicateFreeWalksForEntities(entitySelector.getEntities(), numberOfThreads, numberOfWalksPerEntity, depth);
    }

    @Override
    public void generateRandomWalksDuplicateFree(int numberOfThreads, int numberOfWalksPerEntity, int depth) {
        generateRandomWalksDuplicateFree(numberOfThreads, numberOfWalksPerEntity, depth, DEFAULT_WALK_FILE_TO_BE_WRITTEN);
    }

    @Override
    public void generateRandomMidWalks(int numberOfThreads, int numberOfWalksPerEntity, int depth) {
        generateRandomMidWalks(numberOfThreads, numberOfWalksPerEntity, depth, DEFAULT_WALK_FILE_TO_BE_WRITTEN);
    }

    @Override
    public void generateRandomMidWalks(int numberOfThreads, int numberOfWalksPerEntity, int depth, String filePathOfFileToBeWritten) {
        if (this.parser == null) {
            LOGGER.error("Parser not initialized. Aborting program");
            return;
        }
        if (!parserIsOk) {
            LOGGER.error("Will not execute walk generation due to parser initialization error.");
            return;
        }
        this.filePath = filePathOfFileToBeWritten;
        generateRandomMidWalksForEntities(entitySelector.getEntities(), numberOfThreads, numberOfWalksPerEntity, depth);
    }

    @Override
    public void generateWeightedMidWalks(int numberOfThreads, int numberOfWalksPerEntity, int depth) {
        generateWeightedMidWalks(numberOfThreads, numberOfWalksPerEntity, depth, DEFAULT_WALK_FILE_TO_BE_WRITTEN);
    }

    @Override
    public void generateWeightedMidWalks(int numberOfThreads, int numberOfWalksPerEntity, int depth, String filePathOfFileToBeWritten) {
        if (this.parser == null) {
            LOGGER.error("Parser not initialized. Aborting program");
            return;
        }
        if (!parserIsOk) {
            LOGGER.error("Will not execute walk generation due to parser initialization error.");
            return;
        }
        this.filePath = filePathOfFileToBeWritten;
        generateWeightedMidWalksForEntities(entitySelector.getEntities(), numberOfThreads, numberOfWalksPerEntity, depth);
    }

    @Override
    public void generateRandomMidWalksDuplicateFree(int numberOfThreads, int numberOfWalksPerEntity, int depth) {
        generateRandomMidWalksDuplicateFree(numberOfThreads, numberOfWalksPerEntity, depth, DEFAULT_WALK_FILE_TO_BE_WRITTEN);
    }

    @Override
    public void generateRandomMidWalksDuplicateFree(int numberOfThreads, int numberOfWalksPerEntity, int depth, String filePathOfFileToBeWritten) {
        if (this.parser == null) {
            LOGGER.error("Parser not initialized. Aborting program");
            return;
        }
        if (!parserIsOk) {
            LOGGER.error("Will not execute walk generation due to parser initialization error.");
            return;
        }
        this.filePath = filePathOfFileToBeWritten;
        generateRandomMidWalksForEntitiesDuplicateFree(entitySelector.getEntities(), numberOfThreads, numberOfWalksPerEntity, depth);
    }


    /**
     * Generate walks for the entities.
     *
     * @param entities        The entities for which walks shall be generated.
     * @param numberOfThreads The number of threads involved in generating the walks.
     * @param numberOfWalks   The number of walks to be generated per entity.
     * @param walkLength      The length of each walk.
     */
    public void generateRandomWalksForEntities(Set<String> entities, int numberOfThreads, int numberOfWalks, int walkLength) {
        File outputFile = new File(filePath);
        outputFile.getParentFile().mkdirs();

        // initialize the writer
        try {
            this.writer = new OutputStreamWriter(new GZIPOutputStream(
                    new FileOutputStream(outputFile, false)), StandardCharsets.UTF_8);
        } catch (Exception e1) {
            LOGGER.error("Could not initialize writer. Aborting process.", e1);
            return;
        }

        // thread pool
        ThreadPoolExecutor pool = new ThreadPoolExecutor(numberOfThreads, numberOfThreads,
                0, TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(entities.size()));

        for (String entity : entities) {
            RandomWalkEntityProcessingRunnable th = new RandomWalkEntityProcessingRunnable(this, entity, numberOfWalks, walkLength);
            pool.execute(th);
        }

        pool.shutdown();
        try {
            pool.awaitTermination(10, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted Exception");
            e.printStackTrace();
        }
        this.close();
    }


    /**
     * Default: Do not change URIs.
     *
     * @param uri The uri to be transformed.
     * @return The URI as it is.
     */
    @Override
    public String shortenUri(String uri) {
        return uri;
    }

    @Override
    public UnaryOperator<String> getUriShortenerFunction() {
        return s -> s;
    }
}
