import './App.css';

import React from "react";
import { makeStyles } from '@material-ui/core/styles';
import Card from '@material-ui/core/Card';
import CardActions from '@material-ui/core/CardActions';
import CardContent from '@material-ui/core/CardContent';
import Button from '@material-ui/core/Button';
import Typography from '@material-ui/core/Typography';
import Grid from '@material-ui/core/Grid';
import axios from 'axios';
import AppBar from '@material-ui/core/AppBar';
import Toolbar from '@material-ui/core/Toolbar';
import CssBaseline from '@material-ui/core/CssBaseline';
import Container from '@material-ui/core/Container';
import TextField from '@material-ui/core/TextField';
import {
  BrowserRouter as Router,
  Switch,
  Route,
  Link,
  useParams
} from "react-router-dom";

const useStyles = makeStyles((theme) => ({
  icon: {
    marginRight: theme.spacing(2),
  },
  heroContent: {
    backgroundColor: theme.palette.background.paper,
    padding: theme.spacing(8, 0, 6),
  },
  heroButtons: {
    marginTop: theme.spacing(4),
  },
  cardGrid: {
    paddingTop: theme.spacing(8),
    paddingBottom: theme.spacing(8),
  },
  card: {
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
  },
  cardMedia: {
    paddingTop: '56.25%', // 16:9
  },
  cardContent: {
    flexGrow: 1,
  },
  title: {
    flexGrow: 1,
  },
  footer: {
    backgroundColor: theme.palette.background.paper,
    padding: theme.spacing(6),
  },
}));

export default function Raffle() {
    let { id } = useParams();
    
    const classes = useStyles();
    const [raffle, setRaffle] = React.useState(
        {
            id: 1,
            name: "raffle_1",
            description: "hey this is raffle_1",
            deadline: 460123,
            erg: 12000000,
            organizer_addr: "9hgtcPbEQnsWMLJ38ATR6ThQgGQpYQFKV1H866Lvcy8Je6bu1wn",
            charity_addr: "9hnWF5YPyjQtoTVs4ArWZmrm1azonBqqY1zk23R7Vqrn5wVtMmK",
            min: 1000000
        });
    /* Get raffle data from back-end */
    /*
    React.useEffect(() => {
        axios.get(`https://jsonplaceholder.typicode.com/users`)
        .then(res => {
        const persons = res.data;
        setRaffles(persons)
        })
    }, []);
    */
    
    return (
    <React.Fragment>
      <CssBaseline />
      <AppBar position="relative">
        <Toolbar>
          <Typography variant="h6" color="inherit" noWrap className={classes.title}>
            X/Y RAFFLE
          </Typography>
          <Link to="/">
            <Button variant="contained">
                Home
            </Button>
          </Link>
        </Toolbar>
      </AppBar>
      <main>
        <Container className={classes.cardGrid} maxWidth="lg">
          {/* End hero unit */}
          <Grid container spacing={4}>
              <Grid item xs={12} md={7}>
                <Card className={classes.card}>
                  <CardContent className={classes.cardContent}>
                    <Typography gutterBottom variant="h5" color="primary" component="h2">
                      {raffle.name}
                    </Typography>
                    <Typography>
                      {raffle.description}
                    </Typography>
                    <Typography color="textSecondary">
                      id: {raffle.id}
                    </Typography>
                    <Typography color="textSecondary">
                      deadline: {raffle.deadline}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={12} md={5}>
                <Card className={classes.card}>
                  <CardContent className={classes.cardContent}>
                    <Typography gutterBottom variant="h5" color="primary" component="h2">
                      Deposit
                    </Typography>
                    <Typography component="p" variant="h5">
                        {raffle.erg / 1000000000} ERG
                    </Typography>
                    <Typography color="textSecondary">
                      min donation: {raffle.min}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={12} md={6}>
                <Card className={classes.card}>
                  <CardContent className={classes.cardContent}>
                    <Typography gutterBottom variant="h5" color="primary" component="h2">
                      Addresses
                    </Typography>
                    <Grid container spacing={4}>
                        <Grid item xs={10}>
                        <TextField
                            fullWidth
                            id="organizerAddr"
                            label="Organizer Address"
                            name="organizerAddr"
                            autoComplete="org_addr"
                            disabled
                            value={raffle.organizer_addr}
                        />
                        </Grid>
                        <Grid item xs={10}>
                        <TextField
                            fullWidth
                            id="charityAddr"
                            label="Charity Address"
                            name="charityAddr"
                            autoComplete="chr_addr"
                            disabled
                            value={raffle.charity_addr}
                        />
                        </Grid>
                    </Grid>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={12} md={6}>
                <Card className={classes.card}>
                  <CardContent className={classes.cardContent}>
                    <Typography gutterBottom variant="h5" color="primary" component="h2">
                      Donate
                    </Typography>
                    <Grid container spacing={4}>
                        <Grid item xs={10}>
                        <TextField
                            fullWidth
                            id="walletAddr"
                            label="Your wallet address"
                            name="walletAddr"
                            autoComplete="wallet_addr"
                        />
                        </Grid>
                        <Grid item xs={10}>
                        <TextField
                            fullWidth
                            id="erg"
                            label="ERG"
                            name="erg"
                            autoComplete="value"
                        />
                        </Grid>
                        <Grid item xs={10}>
                        <Button
                            type="submit"
                            color="primary"
                            className={classes.submit}
                        >
                            Donate
                        </Button>
                        </Grid>
                    </Grid>
                  </CardContent>
                </Card>
              </Grid>
          </Grid>
        </Container>
      </main>
    </React.Fragment>)
}
