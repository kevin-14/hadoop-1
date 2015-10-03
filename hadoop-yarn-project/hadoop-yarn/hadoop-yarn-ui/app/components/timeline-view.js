import Ember from 'ember';

export default Ember.Component.extend({
  canvas: {
    svg: undefined,
    h: 0,
    w: 0,
    tooltip: undefined
  },

  clusterMetrics: undefined,
  modelArr: [],
  colors: d3.scale.category10().range(),

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
    var textWidth = 50;
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
      .range([0, this.canvas.w - 2 * border - textWidth]);

    this.canvas.svg.append("line")
      .attr("x1", border + textWidth)
      .attr("y1", border)
      .attr("x2", this.canvas.w - border)
      .attr("y2", border)
      .attr("class", "grid")
      .attr("fill", "gray");

    // show bar
    var bar = this.canvas.svg.selectAll("bars")
      .data(this.modelArr)
      .enter()
      .append("rect")
      .attr("y", function(d, i) {
        return border + (gap + singleBarHeight) * i;
      })
      .attr("x", function(d, i) {
        return border + textWidth + xScaler(d.get("startTs"));
      })
      .attr("height", singleBarHeight)
      .attr("fill", function(d, i) {
        return this.colors[i];
      }.bind(this))
      .attr("width", function(d, i) {
        var finishedTs = xScaler(d.get("finishedTs"));
        finishedTs = finishedTs > 0 ? finishedTs : xScaler(end);
        return finishedTs - xScaler(d.get("startTs"));
      });

    // show bar texts
    for (var i = 0; i < this.modelArr.length; i++) {
      this.canvas.svg.append("text")
        .text(this.modelArr[i].get(this.get("label")))
        .attr("y", border + (gap + singleBarHeight) * i + singleBarHeight / 2)
        .attr("x", border)
        .attr("class", "bar-chart-text");
    }
  },

  didInsertElement: function() {
    // init model
    this.get("model").forEach(function(o) {
      this.modelArr.push(o);
    }.bind(this));

    this.modelArr.sort(function(a, b) {
      var tsA = a.get("startTs");
      var tsB = b.get("startTs");

      return tsA - tsB;
    });
    var begin = this.modelArr[0].get("startTs");
    var end = 0;
    for (var i = 0; i < this.modelArr.length; i++) {
      var ts = this.modelArr[i].get("finishedTs");
      if (ts > end) {
        end = ts;
      }
    }
    if (end <= 0) {
      end = new Date().getTime();
    }

    this.draw(begin, end);
  },
});