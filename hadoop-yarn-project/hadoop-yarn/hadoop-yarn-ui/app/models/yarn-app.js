import DS from 'ember-data';

export default DS.Model.extend({
	appName: DS.attr('string'),
	user: DS.attr('string'),
	queue: DS.attr('string'),
	state: DS.attr('string'),
	startTime: DS.attr('string'),
	elapsedTime: DS.attr('string')
});