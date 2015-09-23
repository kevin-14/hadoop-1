import Ember from 'ember';
import ChartUtilsMixin from 'yarn-ui/mixins/charts-utils';

export default Ember.Component.extend(ChartUtilsMixin, {
	canvas: {
		svg: undefined,
		w: 0,
		h: 0,
	},

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

	queueColors: d3.scale.category20().range(),

	renderQueue: function(now, depth, sequence) {
		if (depth > this.queues.maxDepth) {
			this.queues.maxDepth = depth;
		}

		var cx = 20 + depth * 30;
		var cy = 20 + sequence * 30;
		var name = now.get("name");

		var g = this.queues.dataGroup.append("g")
			.attr("id", "queue-" + name + "-g");

		var folded = this.queues.foldedQueues[name];
		var isParentQueue = false;

		// render its children
		var children = [];
		var childrenNames = now.get("children");
		if (childrenNames) {
			childrenNames.forEach(function(name) {
				isParentQueue = true;
				var child = this.queues.data[name];
				if (child) {
					children.push(child);
				}
			}.bind(this));
		}
		if (folded) {
			children = [];
		}
		var linefunction = d3.svg.line()
			.interpolate("basis")
			.x(function(d) {
				return d.x;
			})
			.y(function(d) {
				return d.y;
			});

		for (var i = 0; i < children.length; i++) {
			sequence = sequence + 1;
			// Get center of children queue
			var cc = this.renderQueue(children[i],
				depth + 1, sequence);
			g.append("path")
				.attr("class", "queue")
				.attr("d", linefunction([{
					x: cx,
					y: cy
				}, {
					x: cc.x - 20,
					y: cc.y
				}, cc]));
		}

		var circle = g.append("circle")
			.attr("cx", cx)
			.attr("cy", cy)
			.attr("class", "queue");

		circle.on('mouseover', function() {
			circle.style("fill", this.queueColors[1]);
		}.bind(this));
		circle.on('mouseout', function() {
			if (circle != this.queues.selectedQueueCircle) {
				circle.style("fill", this.queueColors[0]);
			}
		}.bind(this));
		circle.on('click', function() {
			circle.style("fill", this.queueColors[2]);
			var pre = this.queues.selectedQueueCircle;
			this.queues.selectedQueueCircle = circle;
			if (pre) {
				pre.on('mouseout')();
			}
			this.renderCharts(name);
		}.bind(this));
		circle.on('dblclick', function() {
			if (!isParentQueue) {
				return;
			}

			if (this.queues.foldedQueues[name]) {
				delete this.queues.foldedQueues[name];
			} else {
				this.queues.foldedQueues[name] = now;
			}
			this.renderQueues();
		}.bind(this));

		var text = name;
		if (folded) {
			text = name + " (+)";
		}

		// print queue's name
		g.append("text")
			.attr("x", cx + 30)
			.attr("y", cy + 5)
			.text(text)
			.attr("class", "queue");

		return {
			x: cx,
			y: cy
		};
	},

	renderQueues: function() {
		if (this.queues.dataGroup) {
			this.queues.dataGroup.remove();
		}
		// render queues
		this.queues.dataGroup = this.canvas.svg.append("g")
			.attr("id", "queues-g");
		var rootQueue = undefined;

		if (this.queues.data) {
			this.renderQueue(this.queues.data['root'], 0, 0);

		}
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

	draw: function() {
		this.queues.data = {};
		this.get("model")
			.forEach(function(o) {
				this.queues.data[o.id] = o;
			}.bind(this));

		// get w/h of the svg
		var bbox = d3.select("#main-container")
			.node()
			.getBoundingClientRect();
		this.canvas.w = bbox.width;
		this.canvas.h = Math.max(Object.keys(this.queues.data)
			.length * 35, this.canvas.w * 9 / 16);

		this.canvas.svg = d3.select("#main-container")
			.append("svg")
			.attr("width", this.canvas.w)
			.attr("height", this.canvas.h)
			.attr("id", "main-svg");

		this.renderBackground();

		this.renderQueues();
		this.renderCharts("root");
	},

	didInsertElement: function() {
		this.draw();
	},

	/*
	 * data = [{label="xx", value=},{...}]
	 */
	renderTable: function(data, title, layout) {
		d3.select("#main-svg")
			.append('table')
			.selectAll('tr')
			.data(data)
			.enter()
			.append('tr')
			.selectAll('td')
			.data(function(d) {
				return d;
			})
			.enter()
			.append('td')
			.text(function(d) {
				return d;
			});
	},

	getLayout: function(index) {
		var cMargin = 30; // margin between each charts
		var perChartWidth = 400;
		var chartPerRow = Math.min(this.charts.w / (perChartWidth + cMargin), 1);

		var perChartHeight = perChartWidth * 0.75 // 4:3 for each chart

		var row = Math.floor(index / chartPerRow);
		var col = index % chartPerRow;

		var x1 = (row + 1) * cMargin + row * perChartWidth + this.charts.leftBannerLen;
		var y1 = (col + 1) * cMargin + col * perChartHeight;
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

	renderQueueCapacities: function(queue, layout) {
		// Render bar chart
		this.renderBarChart(this.charts.g, [{
			label: "Cap",
			value: queue.get("capacity")
		}, {
			label: "MaxCap",
			value: queue.get("maxCapacity")
		}, {
			label: "UsedCap",
			value: queue.get("usedCapacity")
		}], "Queue Capacities", layout, 60);
	},

	renderChildrenCapacities: function(queue, layout) {
		var data = [];
		var children = queue.get("children");
		if (children) {
			for (var i = 0; i < children.length; i++) {
				var child = this.queues.data[children[i]];
				data.push({
					label: child.get("name"),
					value: child.get("capacity")
				});
			}
		}

		this.renderDonutChart(this.charts.g, data, "Children Capacities", layout, true);
	},

	renderChildrenUsedCapacities: function(queue, layout) {
		var data = [];
		var children = queue.get("children");
		if (children) {
			for (var i = 0; i < children.length; i++) {
				var child = this.queues.data[children[i]];
				data.push({
					label: child.get("name"),
					value: child.get("usedCapacity")
				});
			}
		}

		this.renderDonutChart(this.charts.g, data, "Children Used Capacities", layout, true);
	},

	renderLeafQueueUsedCapacities: function(layout) {
		var leafQueueUsedCaps = [];
		for (var queueName in this.queues.data) {
			var q = this.queues.data[queueName];
			if ((!q.get("children")) || q.get("children")
				.length == 0) {
				// it's a leafqueue
				leafQueueUsedCaps.push({
					label: q.get("name"),
					value: q.get("usedCapacity")
				});
			}
		}

		this.renderDonutChart(this.charts.g, leafQueueUsedCaps, "LeafQueues Used Capacities",
			layout, true);
	},

	renderCharts: function(queueName) {
		this.initCharts();

		this.charts.leftBannerLen = this.queues.maxDepth * 30 + 100;
		this.charts.h = this.canvas.h;
		this.charts.w = this.canvas.w - this.charts.leftBannerLen;

		// Separate queue map and charts
		d3.select("#main-svg")
			.append("line")
			.attr("x1", this.charts.leftBannerLen)
			.attr("y1", 0)
			.attr("x2", this.charts.leftBannerLen)
			.attr("y2", "100%")
			.attr("class", "queue-banner");

		var chartG = d3.select("#charts-g");
		if (chartG) {
			chartG.remove();
		}

		// add charts-g
		this.charts.g = d3.select("#main-svg")
			.append("g")
			.attr("id", "charts-g");

		var queue = this.queues.data[queueName];
		var idx = 0;

		if (queue.get("name") == "root") {
			this.renderLeafQueueUsedCapacities(this.getLayout(idx++));
		}
		if (queue.get("name") != "root") {
			this.renderQueueCapacities(queue, this.getLayout(idx++));
		}
		if (queue.get("children") && queue.get("children")
			.length > 0) {
			this.renderChildrenCapacities(queue, this.getLayout(idx++));
			this.renderChildrenUsedCapacities(queue, this.getLayout(idx++));
		}
	},
});