package org.apache.hadoop.yarn.applications.yalp.client.cli.job;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.ResourceInformation;
import org.apache.hadoop.yarn.api.records.ResourceTypeInfo;
import org.apache.hadoop.yarn.applications.yalp.client.RunJobParameters;
import org.apache.hadoop.yarn.applications.yalp.client.cli.AbstractCli;
import org.apache.hadoop.yarn.applications.yalp.client.cli.ClientContext;
import org.apache.hadoop.yarn.applications.yalp.client.cli.CliConstants;
import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.UnitsConversionUtil;
import org.apache.hadoop.yarn.util.resource.ResourceUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RunJobCli extends AbstractCli{
  public RunJobCli(ClientContext cliContext) {
    super(cliContext);
  }

  private Options generateOptions() {
    Options options = new Options();
    options.addOption(CliConstants.NAME, true, "Name of the job");
    options.addOption(CliConstants.INPUT, true,
        "Input of the job, could be local or other FS directory");
    options.addOption(CliConstants.OUTPUT, true,
        "Model output of the job, could be local or other FS directory");
    options.addOption(CliConstants.N_WORKERS, true,
        "Numnber of worker tasks of the job, by default it's 1");
    options.addOption(CliConstants.N_PS, true,
        "Numnber of PS tasks of the job, by default it's 0");
    options.addOption(CliConstants.WORKER_RES, true,
        "Resource of each worker, for example "
            + "memory-mb=2048,vcores=2,yarn.io/gpu=2");
    options.addOption(CliConstants.PS_RES, true,
        "Resource of each PS, for example "
            + "memory-mb=2048,vcores=2,yarn.io/gpu=2");
    options.addOption(CliConstants.DOCKER_IMAGE, true, "Docker image name/tag");
    options.addOption(CliConstants.QUEUE, true,
        "Name of queue to run the job, by default it uses default queue");
    options.addOption(CliConstants.TENSORBOARD, true, "Should we run TensorBoard"
        + " for this job? By default it's true");
    options.addOption(CliConstants.WORKER_LAUNCH_CMD, true,
        "Commandline of worker, all arguments after this command will be "
            + "directly passed to launch the worker");
    return options;
  }

  private void printUsages(Options options) {
    new HelpFormatter().printHelp("run", options);
  }

  private void validateResourceTypes(Iterable<String> resourceNames)
      throws IOException, YarnException {
    List<ResourceTypeInfo> resourceTypes =
        cliContext.getOrCreateYarnClient().getResourceTypeInfo();
    for (String resourceName : resourceNames) {
      if (!resourceTypes.stream().anyMatch(e ->
          e.getName().equals(resourceName))) {
        throw new ResourceNotFoundException("Unknown resource: " +
            resourceName);
      }
    }
  }

  // TODO, this duplicated to Client of distributed shell, should cleanup
  private Map<String, Long> parseResourcesString(String resourcesStr) {
    Map<String, Long> resources = new HashMap<>();

    // Ignore the grouping "[]"
    if (resourcesStr.startsWith("[")) {
      resourcesStr = resourcesStr.substring(1);
    }
    if (resourcesStr.endsWith("]")) {
      resourcesStr = resourcesStr.substring(0, resourcesStr.length());
    }

    for (String resource : resourcesStr.trim().split(",")) {
      resource = resource.trim();
      if (!resource.matches("^[^=]+=\\d+\\s?\\w*$")) {
        throw new IllegalArgumentException("\"" + resource + "\" is not a " +
            "valid resource type/amount pair. " +
            "Please provide key=amount pairs separated by commas.");
      }
      String[] splits = resource.split("=");
      String key = splits[0], value = splits[1];
      String units = ResourceUtils.getUnits(value);
      String valueWithoutUnit = value.substring(
          0, value.length() - units.length()).trim();
      Long resourceValue = Long.valueOf(valueWithoutUnit);
      if (!units.isEmpty()) {
        resourceValue = UnitsConversionUtil.convert(units, "Mi", resourceValue);
      }
      if (key.equals("memory")) {
        key = ResourceInformation.MEMORY_URI;
      }
      resources.put(key, resourceValue);
    }
    return resources;
  }

  public Resource createResourceFromString(String resourceStr)
      throws IOException, YarnException {
    Map<String, Long> typeToValue = parseResourcesString(resourceStr);
    validateResourceTypes(typeToValue.keySet());
    Resource resource = Resource.newInstance(0, 0);
    for (Map.Entry<String, Long> entry : typeToValue.entrySet()) {
      resource.setResourceValue(entry.getKey(), entry.getValue());
    }
    return resource;
  }

  @Override
  public void run(String[] args)
      throws ParseException, IOException, YarnException {
    Options options = generateOptions();

    // Search for WORKER_LAUNCH_CMD, and skip all followed commands
    int i;
    for (i = 0; i < args.length; i++) {
      if (args[i].equals("--" + CliConstants.WORKER_LAUNCH_CMD) || args[i].equals(
          "-" + CliConstants.WORKER_LAUNCH_CMD)) {
        break;
      }
    }

    String[] launchArgs = null;
    String[] argsForParsing = args;
    if (i < args.length) {
      argsForParsing = Arrays.copyOfRange(args, 0, i);
      launchArgs = Arrays.copyOfRange(args, i + 1, args.length);
    }

    // Do parsing
    GnuParser parser = new GnuParser();
    CommandLine cli = parser.parse(options, argsForParsing);

    String name = cli.getOptionValue(CliConstants.NAME);
    if (name == null) {
      printUsages(options);
      throw new ParseException("--name is absent");
    }

    String input = cli.getOptionValue(CliConstants.INPUT);
    String output = cli.getOptionValue(CliConstants.OUTPUT);
    int nWorkers = 1;
    if (cli.getOptionValue(CliConstants.N_WORKERS) != null) {
      nWorkers = Integer.parseInt(cli.getOptionValue(CliConstants.N_WORKERS));
    }

    int nPS = 0;
    if (cli.getOptionValue(CliConstants.N_PS) != null) {
      nPS = Integer.parseInt(cli.getOptionValue(CliConstants.N_PS));
    }

    String workerResourceStr = cli.getOptionValue(CliConstants.WORKER_RES);
    if (workerResourceStr == null) {
      printUsages(options);
      throw new ParseException("--" + CliConstants.WORKER_RES + " is absent.");
    }
    Resource workerResource = createResourceFromString(workerResourceStr);

    Resource psResource = null;
    if (nPS > 0) {
      String psResourceStr = cli.getOptionValue(CliConstants.PS_RES);
      if (psResourceStr == null) {
        printUsages(options);
        throw new ParseException("--" + CliConstants.PS_RES + " is absent.");
      }
      psResource = createResourceFromString(psResourceStr);
    }

    String dockerImage = cli.getOptionValue(CliConstants.DOCKER_IMAGE);
    if (dockerImage == null) {
      printUsages(options);
      throw new ParseException("--" + CliConstants.DOCKER_IMAGE + " is absent.");
    }

    boolean tensorboard = true;
    if (cli.getOptionValue(CliConstants.TENSORBOARD) != null) {
      tensorboard = Boolean.parseBoolean(
          cli.getOptionValue(CliConstants.TENSORBOARD));
    }

    RunJobParameters param = new RunJobParameters();
    param.setInput(input).setName(name).setNumPS(nPS).setNumWorkers(nWorkers)
        .setOutput(output).setPsResource(psResource).setWorkerResource(
        workerResource).setTensorboardEnabled(tensorboard).setWorkerLaunchCmd(
        launchArgs).setDockerImageName(dockerImage);
  }
}
