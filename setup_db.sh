#!/bin/sh
su - postgres -c "psql -c \"CREATE USER yukira WITH SUPERUSER PASSWORD 'yukira_dev';\""
su - postgres -c "psql -c \"CREATE DATABASE yukira OWNER yukira;\""
