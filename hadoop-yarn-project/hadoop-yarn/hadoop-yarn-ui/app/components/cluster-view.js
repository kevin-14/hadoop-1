import Ember from 'ember';
import ChartUtilsMixin from 'yarn-ui/mixins/charts-utils';

export default Ember.Component.extend(ChartUtilsMixin, {
  queues: {
    data: undefined,
    foldedQueues: {},
    selectedQueueCircle: undefined,
    maxDepth: -1,
  },

  charts: {
    svg: undefined,
    leftBannerLen: 0,
    h: 0,
    w: 0,
    tooltip: undefined
  },

  clusterMetrics: undefined,

  draw: function() {
    // initialize model
    this.get("model")
      .forEach(function(o) {
        this.clusterMetrics = o;
      }.bind(this));

    // get w/h of the svg
    var bbox = d3.select("#main-container")
      .node()
      .getBoundingClientRect();
    this.canvas.w = bbox.width;
    this.canvas.h = 1200;

    this.canvas.svg = d3.select("#main-container")
      .append("svg")
      .attr("width", this.canvas.w)
      .attr("height", this.canvas.h)
      .attr("id", "main-svg");

    this.renderBackground();

    this.renderCharts();
  },

  renderCharts: function() {
    this.initCharts();

    var idx = 0;
    this.renderFinishedApps(this.getLayout(idx++));
    this.renderRunningApps(this.getLayout(idx++));
    this.renderNodes(this.getLayout(idx++));
    this.renderResource(this.getLayout(idx++), "MB", "Memory", "MB");
    this.renderResource(this.getLayout(idx++), "VirtualCores", "VCores");
  },

  didInsertElement: function() {
    this.draw();
  },

  renderFinishedApps: function(layout) {
    var arr = [];
    arr.push({
      label: "Completed",
      value: this.clusterMetrics.get("appsCompleted")
    });
    arr.push({
      label: "Killed",
      value: this.clusterMetrics.get("appsKilled")
    });
    arr.push({
      label: "Failed",
      value: this.clusterMetrics.get("appsFailed")
    });

    this.renderDonutChart(this.charts.g, arr, "Finished Apps",
      layout, true);
  },

  renderRunningApps: function(layout) {
    var arr = [];

    arr.push({
      label: "Pending",
      value: this.clusterMetrics.get("appsPending")
    });
    arr.push({
      label: "Running",
      value: this.clusterMetrics.get("appsRunning")
    });

    this.renderDonutChart(this.charts.g, arr, "Running Apps",
      layout, true);
  },

  renderNodes: function(layout) {
    var arr = [];
    arr.push({
      label: "Active",
      value: this.clusterMetrics.get("activeNodes")
    });
    arr.push({
      label: "Unhealthy",
      value: this.clusterMetrics.get("unhealthyNodes")
    });
    arr.push({
      label: "Decomissioned",
      value: this.clusterMetrics.get("decommissionedNodes")
    });

    this.renderDonutChart(this.charts.g, arr, "Nodes",
      layout, true, "Actived", this.clusterMetrics.get("activeNodes"));
  },

  renderResource: function(layout, type, title, unit = undefined) {
    var arr = [];
    arr.push({
      label: "Allocated",
      value: this.clusterMetrics.get("allocated" + type)
    });
    arr.push({
      label: "Reserved",
      value: this.clusterMetrics.get("reserved" + type)
    });
    arr.push({
      label: "Available",
      value: this.clusterMetrics.get("available" + type)
    });

    this.renderDonutChart(this.charts.g, arr, "Resource: " + title + (unit ? " (" + unit + ")": ""),
      layout, true, "Total", this.clusterMetrics.get("total" + type));
  },
});