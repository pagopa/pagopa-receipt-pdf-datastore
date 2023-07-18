// Get Cosmos Client
import  Express  from "express";
import {createDocument} from "../controllers/cosmosController.js";

const documentRouter = Express.Router();

documentRouter.post("/createDocument", createDocument);

export {
    documentRouter
}


