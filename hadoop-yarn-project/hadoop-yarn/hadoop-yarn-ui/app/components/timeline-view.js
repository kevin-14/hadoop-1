import Ember from 'ember';
import Converter from 'yarn-ui/utils/converter';

export default Ember.Component.extend({
  canvas: {
    svg: undefined,
    h: 0,
    w: 0,
    tooltip: undefined
  },

  clusterMetrics: undefined,
  modelArr: [],

  draw: function(start, end) {
    // get w/h of the svg
    var bbox = d3.select("#" + this.get("parent-id"))
      .node()
      .getBoundingClientRect();
    this.canvas.w = bbox.width;
    this.canvas.h = this.get("height");

    this.canvas.svg = d3.select("#" + this.get("parent-id"))
      .append("svg")
      .attr("width", this.canvas.w)
      .attr("height", this.canvas.h)
      .attr("id", this.get("my-id"));

    this.renderTimeline(start, end);
  },

  renderTimeline: function(start, end) {
    var border = 30;
    var singleBarHeight = 30;
    var gap = 5;

    /*
     start-time                              end-time
      |--------------------------------------|
         ==============
                ==============
                        ==============
                              ===============
     */
    var xScaler = d3.scale.linear()
      .domain([start, end])
      .range([0, this.canvas.w - 2 * border]);

    // show bar
    var bar = this.canvas.svg.selectAll("bars")
      .data(this.modelArr)
      .enter()
      .append("rect")
      .attr("y", function(d, i) {
        return border + (gap + singleBarHeight) * i;
      })
      .attr("x", function(d, i) {
        return border + xScaler(Converter.dateToTimeStamp(d.get("startTime")));
      })
      .attr("height", singleBarHeight)
      .attr("fill", function(d, i) {
        return "Black";
      }.bind(this))
      .attr("width", 100);
  },

  didInsertElement: function() {
    // init model
    this.get("model").forEach(function(o) {
      this.modelArr.push(o);
    }.bind(this));

    this.modelArr.sort(function(a, b) {
      var tsA = Converter.dateToTimeStamp(a.get("startTime"));
      var tsB = Converter.dateToTimeStamp(b.get("startTime"));

      return tsA - tsB;
    });
    var begin = Converter.dateToTimeStamp(this.modelArr[0].get("startTime"));
    var end = 0;
    for (var i = 0; i < this.modelArr.length; i++) {
      var ts = Converter.dateToTimeStamp(this.modelArr[i].get("finishedTime"));
      if (ts > end) {
        end = ts;
      }
    }
    if (end == 0) {
      end = new Date().getTime();
    }

    this.draw(begin, end);
  },
});