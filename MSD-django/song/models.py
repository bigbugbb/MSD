from django.db import models

class ProfileManager(models.Manager):
	def create_profile(self, trackid, releaseid, centroid, fromtest):
		profile = self.create(trackid=trackid, releaseid=releaseid, centroid=centroid, fromtest=fromtest)
		return profile

class Profile(models.Model):

	def __str__(self):
		return str(self.year) + ',' + str(self.title) + ',' + str(self.artist)

	trackid   = models.TextField(unique=True) # Echo Nest track id
	releaseid = models.IntegerField() # 7digital release id
	centroid  = models.IntegerField(null=True)	
	year      = models.IntegerField(null=True)
	title     = models.TextField(null=True)
	artist    = models.TextField(null=True)
	image     = models.TextField(null=True)
	fromtest  = models.NullBooleanField(null=True, default=True) # whether the data is imported from test data

	objects = ProfileManager()


