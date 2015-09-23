import Ember from 'ember';

export default Ember.Mixin.create({
  tooltip : undefined,
  colors: d3.scale.category10().range(),

  initCharts: function() {
    this.tooltip = d3.select("body")
      .append("div")
      .attr("class", "tooltip")
      .attr("id", "chart-tooltip")
      .style("opacity", 0);
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
    var maxBarWidth = layout.x2 - layout.x1 - 2 * layout.margin - textWidth;

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
  },

  /*
   * data = [{label="xx", value=},{...}]
   */
  renderDonutChart: function(chartsG, data, title, layout, showLabels = false) {
    console.log(data);

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
      v = Math.max(v, 1e-3);
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
        return this.colors[i];
      }.bind(this))
      .attr("d", arc);
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
          return d.label;
        });
    }

    path.transition()
      .duration(500)
      .attrTween('d', tweenPie);
  },
});
