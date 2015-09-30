import Ember from 'ember';

export default Ember.Mixin.create({
  canvas: {
    svg: undefined,
    w: 0,
    h: 0,
  },

  charts: {
    svg: undefined,
    leftBannerLen: 0,
    h: 0,
    w: 0,
    tooltip: undefined
  },
  
  tooltip : undefined,
  colors: d3.scale.category10().range(),

  initCharts: function() {
    this.tooltip = d3.select("body")
      .append("div")
      .attr("class", "tooltip")
      .attr("id", "chart-tooltip")
      .style("opacity", 0);

    this.charts.h = this.canvas.h;
    this.charts.w = this.canvas.w - this.charts.leftBannerLen;

    // Separate queue map and charts
    if (this.charts.leftBannerLen > 0) {
      d3.select("#main-svg")
        .append("line")
        .attr("x1", this.charts.leftBannerLen)
        .attr("y1", 0)
        .attr("x2", this.charts.leftBannerLen)
        .attr("y2", "100%")
        .attr("class", "chart-leftbanner");
    }

    var chartG = d3.select("#charts-g");
    if (chartG) {
      chartG.remove();
    }

    // add charts-g
    this.charts.g = d3.select("#main-svg")
      .append("g")
      .attr("id", "charts-g");
  },

  renderTitleAndBG: function(g, title, layout) {
    var bg = g.append("g");
    bg.append("text")
      .text(title)
      .attr("x", (layout.x1 + layout.x2) / 2)
      .attr("y", layout.y1 + layout.margin + 20)
      .attr("class", "chart-title");

    bg.append("rect")
      .attr("x", layout.x1)
      .attr("y", layout.y1)
      .attr("width", layout.x2 - layout.x1)
      .attr("height", layout.y2 - layout.y1)
      .attr("class", "chart-frame");
  },

  bindTooltip: function(d) {
    d.on("mouseover", function(d) {
        this.tooltip
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - 28) + "px");
      }.bind(this))
      .on("mousemove", function(d) {
        // Handle pie chart case
        var data = d;
        if (d.data) {
          data = d.data;
        }

        this.tooltip.style("opacity", .9);
        this.tooltip.html(data.label + " = " + data.value)
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - 28) + "px");
      }.bind(this))
      .on("mouseout", function(d) {
        this.tooltip.style("opacity", 0);
      }.bind(this));
  },

  renderBackground: function() {
    // render grid
    var g = this.canvas.svg.append("g")
      .attr("id", "grid-g");

    var gridLen = 30;

    for (var i = 1; i < 200; i++) {
      g.append("line")
        .attr("x1", i * gridLen)
        .attr("x2", i * gridLen)
        .attr("y1", 0)
        .attr("y2", this.canvas.h)
        .attr("stroke", "whiteSmoke");
    }

    for (var j = 1; j < 200; j++) {
      g.append("line")
        .attr("x1", 0)
        .attr("x2", this.canvas.w)
        .attr("y1", j * gridLen)
        .attr("y2", j * gridLen)
        .attr("class", "grid")

    }
  },

  // data: 
  //    [{label=label1, value=value1}, ...]
  //    ...
  renderBarChart: function(chartsGroup, data, title, layout, textWidth = 50) {
    var g = chartsGroup.append("g")
      .attr("id", "chart-" + layout.id);
    this.renderTitleAndBG(g, title, layout);

    var maxValue = -1;
    for (var i = 0; i < data.length; i++) {
      if (data[i] instanceof Array) {
        if (data[i][0].value > maxValue) {
          maxValue = data[i][0].value;
        }
      } else {
        if (data[i].value > maxValue) {
          maxValue = data[i].value;
        }
      }
    }

    var singleBarHeight = 30;

    // 50 is for text
    var maxBarWidth = layout.x2 - layout.x1 - 2 * layout.margin - 2 * textWidth;

    // 30 is for title
    var maxBarsHeight = layout.y2 - layout.y1 - 2 * layout.margin - 30;
    var gap = (maxBarsHeight - data.length * singleBarHeight) / (data.length -
      1);

    var xScaler = d3.scale.linear()
      .domain([0, maxValue])
      .range([0, maxBarWidth]);

    // show bar text
    for (var i = 0; i < data.length; i++) {
      g.append("text")
        .text(
          function() {
            return data[i].label;
          })
        .attr("y", function() {
          return layout.y1 + singleBarHeight / 2 + layout.margin + (gap +
            singleBarHeight) * i + 30;
        })
        .attr("x", layout.x1 + layout.margin);
    }

    // show bar
    var bar = g.selectAll("bars")
      .data(data)
      .enter()
      .append("rect")
      .attr("y", function(d, i) {
        return layout.y1 + 30 + layout.margin + (gap + singleBarHeight) * i;
      })
      .attr("x", layout.x1 + layout.margin + textWidth)
      .attr("height", singleBarHeight)
      .attr("fill", function(d, i) {
        return this.colors[i];
      }.bind(this))
      .attr("width", 0);

    this.bindTooltip(bar);

    bar.transition()
      .duration(500)
      .attr("width", function(d) {
        var w;
        w = xScaler(d.value);
        // At least each item has 3 px
        w = Math.max(w, 3);
        return w;
      });

    // show bar value
    for (var i = 0; i < data.length; i++) {
      g.append("text")
        .text(
          function() {
            return data[i].value;
          })
        .attr("y", function() {
          return layout.y1 + singleBarHeight / 2 + layout.margin + (gap +
            singleBarHeight) * i + 30;
        })
        .attr("x", layout.x1 + layout.margin + textWidth + 15 + xScaler(data[i].value));
    }
  },

  /*
   * data = [{label="xx", value=},{...}]
   */
  renderDonutChart: function(chartsG, data, title, layout, showLabels = false, 
    middleLabel = "Total", middleValue = undefined) {

    var total = 0;
    var allZero = true;
    for (var i = 0; i < data.length; i++) {
      total += data[i].value;
      if (data[i].value > 1e-6) {
        allZero = false;
      }
    }

    if (!middleValue) {
      middleValue = total;
    }

    var g = chartsG.append("g")
      .attr("id", "chart-" + layout.id);
    this.renderTitleAndBG(g, title, layout);

    //Width and height
    var h = layout.y2 - layout.y1;

    // 50 is for title
    var outerRadius = (h - 50 - 2 * layout.margin) / 2;
    var innerRadius = outerRadius * 0.618;
    var arc = d3.svg.arc()
      .innerRadius(innerRadius)
      .outerRadius(outerRadius);

    var cx;
    var cy = layout.y1 + 50 + layout.margin + outerRadius;
    if (showLabels) {
      cx = layout.x1 + layout.margin + outerRadius;
    } else {
      cx = (layout.x1 + layout.x2) / 2;
    }

    var pie = d3.layout.pie();
    pie.sort(null);
    pie.value(function(d) {
      var v = d.value;
      // make sure it > 0
      v = Math.max(v, 1e-6);
      return v;
    });

    //Set up groups
    var arcs = g
      .selectAll("g.arc")
      .data(pie(data))
      .enter()
      .append("g")
      .attr("class", "arc")
      .attr("transform", "translate(" + cx + "," + cy + ")");

    function tweenPie(finish) {
      var start = {
        startAngle: 0,
        endAngle: 0
      };
      var i = d3.interpolate(start, finish);
      return function(d) {
        return arc(i(d));
      };
    }

    //Draw arc paths
    var path = arcs.append("path")
      .attr("fill", function(d, i) {
        if (d.value > 1e-6) {
          return this.colors[i];
        } else {
          return "white";
        }
      }.bind(this))
      .attr("d", arc)
      .attr("stroke", function(d, i) {
        if (allZero) {
          return this.colors[i];
        }
      }.bind(this))
      .attr("stroke-dasharray", function(d, i) {
        if (d.value <= 1e-6) {
          return "10,10";
        }
      }.bind(this));
    this.bindTooltip(path);

    // Show labels
    if (showLabels) {
      var lx = layout.x1 + layout.margin + outerRadius * 2 + 30;
      var squareW = 15;
      var margin = 10;

      var select = g.selectAll(".rect")
        .data(data)
        .enter();
      select.append("rect")
        .attr("fill", function(d, i) {
          return this.colors[i];
        }.bind(this))
        .attr("x", lx)
        .attr("y", function(d, i) {
          return layout.y1 + 50 + (squareW + margin) * i + layout.margin;
        })
        .attr("width", squareW)
        .attr("height", squareW);
      select.append("text")
        .attr("x", lx + squareW + margin)
        .attr("y", function(d, i) {
          return layout.y1 + 50 + (squareW + margin) * i + layout.margin + squareW / 2;
        })
        .text(function(d) {
          return d.label + ' = ' + d.value;
        });
    }

    if (middleLabel) {
      var highLightColor = this.colors[0];
      g.append("text").text(middleLabel).attr("x", cx).attr("y", cy - 20).
        attr("class", "donut-highlight-text").attr("fill", highLightColor);
      g.append("text").text(middleValue).attr("x", cx).attr("y", cy + 30).
        attr("class", "donut-highlight-text").attr("fill", highLightColor).
        style("font-size", "40px");
    }

    path.transition()
      .duration(500)
      .attrTween('d', tweenPie);
  },

  getLayout: function(index) {
    var cMargin = 30; // margin between each charts
    var perChartWidth = 400;
    var chartPerRow = Math.max(Math.floor(this.charts.w / (perChartWidth + cMargin)), 1);

    var perChartHeight = perChartWidth * 0.75 // 4:3 for each chart

    var row = Math.floor(index / chartPerRow);
    var col = index % chartPerRow;

    var x1 = (col + 1) * cMargin + col * perChartWidth + this.charts.leftBannerLen;
    var y1 = (row + 1) * cMargin + row * perChartHeight;
    var x2 = x1 + perChartWidth;
    var y2 = y1 + perChartHeight;

    var layout = {
      x1: x1,
      y1: y1,
      x2: x2,
      y2: y2,
      id: index,
      margin: 10
    };
    return layout;
  },
});
