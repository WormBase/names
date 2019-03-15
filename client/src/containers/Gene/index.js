import React from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import {
  withStyles,
  Button,
  ErrorBoundary,
  Page,
  PageMain,
  Typography,
} from '../../components/elements';

import GeneProfile from './GeneProfile';
import GeneCreate from './GeneCreate';
import GeneDirectory from './GeneDirectory';
import GeneSearchBox from './GeneSearchBox';
import RecentActivities from './RecentActivities';

export { GeneProfile, GeneCreate, GeneDirectory };
