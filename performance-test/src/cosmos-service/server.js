import Express from "express";
import {documentRouter} from "./routes/document.js";

const app = Express();

app.use(Express.json());

app.use("/", documentRouter);

const listener = app.listen(process.env.PORT || 8079, () => {
    console.log('App is listening on port ' + listener.address().port)
})