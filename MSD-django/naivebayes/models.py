from django.db import models

class Range(models.Model):

	def __str__(self):
		return str(self.year) + ',' + str(self.min) + ',' + str(self.max) + ',' + str(self.step)

	min  = models.FloatField()
	max  = models.FloatField()
	step = models.IntegerField()

class Count(models.Model):

	def __str__(self):
		return str(self.year) + ',' + str(self.prob)

	year  = models.IntegerField(unique=True)
	count = models.IntegerField()
	prob  = models.FloatField()

class NBModel(models.Model):

	def __str__(self):
		return str(self.year) + ',' + self.fprob[:64] + '...'

	year  = models.IntegerField(unique=True)
	fprob = models.TextField()

class TestData(models.Model):

	def __str__(self):
		return str(self.year) + ',' + str(self.releaseid) + ',' + self.features[:64] + '...'

	year = models.IntegerField()
	releaseid = models.IntegerField(db_index=True) # 7digital release id
	features = models.TextField()

class TestResult(models.Model):

	def __str__(self):
		return str(self.data) + ',' + str(self.result) + ',' + str(self.correct)

	data    = models.ForeignKey(TestData, unique=True)
	result  = models.IntegerField()
	correct = models.BooleanField(default=False)