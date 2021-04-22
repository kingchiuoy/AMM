package com.graphhopper.matching.cli;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.http.WebHelper;
import com.graphhopper.matching.*;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.ev.DefaultEncodedValueFactory;
import com.graphhopper.routing.util.DefaultFlagEncoderFactory;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.*;
import org.apache.commons.io.FileUtils;
import com.graphhopper.matching.MapMatching;

import java.io.*;
import java.util.*;

import static com.graphhopper.util.Parameters.Routing.MAX_VISITED_NODES;

public class testSecond {

    public List<Observation> tracks;
    public List<Observation> grounds;


    public testSecond() {
        tracks = new ArrayList<>();
        grounds = new ArrayList<>();
//        readFile("map-data/single_order.csv");
    }

    public static void main(String[] args) {
        testSecond app = new testSecond();

        String[] methods = {
                "direct", "hmm", "fmm", "conf_noback", "check_avg"
        };

        Map<Integer, Double> groundMap = app.readGroundMap();

        double[] scores = {0.2, 0.4, 0.6, 0.8};

//        long[] SRarray = {
//                2, 10, 30, 60, 120, 240
//        };
        long[] SRarray = {
                2
        };
        try {
            FileUtils.cleanDirectory(new File("graph-cache"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        String mapPath = "map-data/shanghai.osm.pbf";
        System.out.println(mapPath);
//                String mapPath = "map-data/city/moscow.osm.pbf";
//        if(!new File(mapPath).exists()) continue;

        GraphHopperConfig graphHopperConfiguration = new GraphHopperConfig();
        String vehicle = "car";
        String ghFolder = "graph-cache";
        graphHopperConfiguration.putObject("graph.flag_encoders", vehicle);
        graphHopperConfiguration.putObject("datareader.file", mapPath);
        graphHopperConfiguration.putObject("graph.location", ghFolder);

        String weightingStr = "fastest";
        List<Profile> profiles = new ArrayList<>();
        for (String v : vehicle.split(",")) {
            v = v.trim();
            profiles.add(new Profile(v + "_profile").setVehicle(v).setWeighting(weightingStr));
        }
        graphHopperConfiguration.setProfiles(profiles);
        GraphHopper hopper = new GraphHopperOSM().init(graphHopperConfiguration);
        hopper.importOrLoad();


        if (Helper.isEmpty(vehicle))
            vehicle = EncodingManager.create(new DefaultEncodedValueFactory(), new DefaultFlagEncoderFactory(), ghFolder).fetchEdgeEncoders().get(0).toString();
        // Penalizing inner-link U-turns only works with fastest weighting, since
        // shortest weighting does not apply penalties to unfavored virtual edges.
        Profile profile = new Profile(vehicle + "_profile").setVehicle(vehicle).setWeighting(weightingStr).setTurnCosts(false);
        graphHopperConfiguration.setProfiles(Collections.singletonList(profile));
        hopper = new GraphHopperOSM().init(graphHopperConfiguration);
        hopper.importOrLoad();
        System.out.println("loading graph from cache");
//        hopper.load(graphHopperConfiguration.getString("graph.location", ghFolder));

        PMap hints = new PMap().putObject(MAX_VISITED_NODES, "3000");
        hints.putObject("profile", profile.getName());
        MapMatching mapMatching = new MapMatching(hopper, hints);
        mapMatching.setTransitionProbabilityBeta(2.0);
        mapMatching.setMeasurementErrorSigma(25);


        StopWatch importSW = new StopWatch();


        Translation tr = new TranslationMap().doImport().getWithFallBack(Helper.getLocale("instructions"));//args.getString("instructions")));
        final boolean withRoute = "instruction".isEmpty();//!args.getString("instructions").isEmpty();
        XmlMapper xmlMapper = new XmlMapper();

        Weighting weighting = hopper.createWeighting(hopper.getProfiles().get(0), hints);

        int methodCount = 3;
        mapMatching.setMeasurementErrorSigma(25);
        String out = "map-data/new-data-alg/" + methods[methodCount] + "result-radius200" + mapMatching.getMeasurementErrorSigma() + ".txt";
        String runtimeOut = "map-data/new_runtime/" + methods[methodCount] +  "runtime-radius200" + mapMatching.getMeasurementErrorSigma() + ".txt";
        try {
            FileWriter fwriter = new FileWriter(out);
            fwriter.write("");
            fwriter.close();
            fwriter = new FileWriter(runtimeOut);
            fwriter.write("");
            fwriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i = 1; i <= 1466; i++) {

            String str = String.format("%04d", i);
            String tail = str + ".csv";
            StopWatch matchSW = new StopWatch();
            matchSW.start();
            app.tracks.clear();
            app.readFile("map-data/second-data/new_track/No" + tail);
            for (long sr : SRarray) {


                try {
                    importSW.start();
                    importSW.stop();

                    mapMatching.SR = sr;
                    mapMatching.scoreThre = 0.45;
                    mapMatching.windowSize = 5;
//                            app.writeTriMap(xiujianOb, "map-data/second-data/new_trip_track/" + i + ".txt");

                    MatchResult mr = mapMatching.matchWithSpecificMethod(app.tracks, methodCount);
                    matchSW.stop();
                    app.writeCandidateMap(mapMatching.candidateMap, "map-data/second-data/candidate_points/radius200" + i  + ".txt");
                    app.writeMatchedPoints(mapMatching.matchedPoints, "map-data/second-data/matched_points/radius200" + i  + ".txt");

                    matchSW.stop();
                    try {
                        FileWriter fwriter = new FileWriter(out, true);
                        double groundLength = groundMap.get(i);
                        double matchLength = mr.getMatchLength();
                        double tmp = Math.min(1.0, Math.abs(groundLength - matchLength) / groundLength);
                        double rate = 1.0 - tmp;
                        fwriter.write(i + "," + mr.getMatchLength() + ","
                                + groundLength + "," + rate);
                        fwriter.write("\n");
                        fwriter.close();

                        fwriter = new FileWriter(runtimeOut, true);
                        fwriter.write(i +  "," +  mapMatching.runTime + "," + matchSW.getSeconds()
                                +"," + mapMatching.observationPoints);
                        fwriter.write("\n");
                        fwriter.close();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }

            }
        }
    }
//Original 11

    public void writeMatchedPoints(List<String> points, String out) {

        try {
            FileWriter fwriter = new FileWriter(out);
            fwriter.write("");
            fwriter.close();
            FileWriter fileWriter = new FileWriter(out, true);
            for(String str : points) {
                fileWriter.write(str);
                fileWriter.write("\n");
            }
            fileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void writeCandidateMap(Map<Integer, List<String>> map, String out) {

        try {
            FileWriter fwriter = new FileWriter(out);
            fwriter.write("");
            fwriter.close();
            fwriter = new FileWriter(out, true);
            for(Map.Entry<Integer, List<String>> entry : map.entrySet()) {
                for(String str : entry.getValue()) {
                    StringBuilder tmp = new StringBuilder();
                    tmp.append(entry.getKey());
                    tmp.append(",");
                    tmp.append(str);
                    tmp.append("\n");
                    fwriter.write(tmp.toString());
                }
            }
            fwriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public void writeTriMap(List<Observation> observations, String out) {

        try {
            FileWriter fwriter = new FileWriter(out, true);

            for(Observation o : observations) {
                fwriter.write(o.getPoint().lat +  ","
                        + o.getPoint().lon);
//                                System.out.println(rate);
                fwriter.write("\n");

            }


            fwriter.close();

//                                fwriter = new FileWriter("map-data/new_runtime/" + methods[methodCount] +  "runtime_real.txt", true);
//                                fwriter.write(i +  "," + mapMatching.runTime + "," + matchSW.getSeconds()
//                                        +"," + mapMatching.observationPoints);
//                                fwriter.write("\n");
//                                fwriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public  void readFile(String file) {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(
                    file
            ));
            String line = reader.readLine();
            line = reader.readLine();
//            System.out.println(line);
            while (line != null) {
                String[] splits = line.split(",");
                long timestamp = Long.parseLong(splits[1]) / 1000;
                double lat = Double.parseDouble(splits[2]);
                double lon = Double.parseDouble(splits[3]);
                double speed = Double.parseDouble(splits[4]);
                double direction = Double.parseDouble(splits[5]);
                double acc = Double.parseDouble(splits[6]);
                tracks.add(new Observation(timestamp, lat, lon, acc, speed, direction));

//                System.out.println("Time" + timestamp + " " + lat + " " + lon + " " + speed);
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public  void readGroundFile(String file) {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(
                    file
            ));
            String line = reader.readLine();
            line = reader.readLine();
//            System.out.println(line);
            while (line != null) {
                String[] splits = line.split(",");
                long timestamp = 0;
                double lat = Double.parseDouble(splits[2]);
                double lon = Double.parseDouble(splits[3]);
                double speed = 0.0;
                double direction = 0.0;
                grounds.add(new Observation(timestamp, lat, lon));

//                System.out.println("Time" + timestamp + " " + lat + " " + lon + " " + speed);
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public Map<Integer, Double> readGroundMap() {
        Map<Integer, Double> result = new HashMap<>();
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(
                    "map-data/new-data-alg/conf_nobackground-length.txt"

            ));
            String line = reader.readLine();
//            System.out.println(line);
            while (line != null) {
                String[] splits = line.split(",");
                int id = Integer.parseInt(splits[0]);
                double len = Double.parseDouble(splits[1]);
                result.put(id, len);

                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }


    public  void readGpxFile(String file) {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(
                    file
            ));
            String line = reader.readLine();
//            System.out.println(line);
            while (line != null) {
                if(line.contains("lat")) {
                    long timestamp = 0;
                    String[] splits = line.split("\"");
                    System.out.println(splits[1]);
                    System.out.println(splits[3]);
                    double lat = Double.parseDouble(splits[1]);
                    double lon = Double.parseDouble(splits[3]);
                    grounds.add(new Observation(timestamp, lat, lon));
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void printMatchResult(MatchResult mr,  int number) {
        String out = "map-data/new-data-alg/matchedTrack/" + number +  ".txt";
        try {
            FileWriter fwriter = new FileWriter(out, true);



//                                fwriter = new FileWriter("map-data/new_runtime/" + methods[methodCount] +  "runtime_real.txt", true);
//                                fwriter.write(i +  "," + mapMatching.runTime + "," + matchSW.getSeconds()
//                                        +"," + mapMatching.observationPoints);
//                                fwriter.write("\n");
//                                fwriter.close();

        for (int emIndex = 0; emIndex < mr.getEdgeMatches().size(); emIndex++) {
            EdgeMatch edgeMatch = mr.getEdgeMatches().get(emIndex);
            for (State extension : edgeMatch.getStates()) {
                StringBuilder str = new StringBuilder();
                str.append(extension.getSnap().getSnappedPoint().lon);
                str.append(",");
                str.append(extension.getSnap().getSnappedPoint().lat);
                fwriter.write(str.toString());
                fwriter.write("\n");
            }
        }



        fwriter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }



    }


}
